package com.bringmycart.bmc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.bringmycart.bmc.identity.domain.Role;
import com.bringmycart.bmc.identity.domain.UserAccount;
import com.bringmycart.bmc.identity.infrastructure.UserAccountRepository;
import com.bringmycart.bmc.identity.security.JwtService;
import com.bringmycart.bmc.savedproducts.application.ProductEnrichmentStateService;
import com.bringmycart.bmc.savedproducts.application.ProductMetadataProvider;
import com.bringmycart.bmc.savedproducts.domain.EnrichmentFailureCategory;
import com.bringmycart.bmc.savedproducts.domain.EnrichmentStatus;
import com.bringmycart.bmc.savedproducts.domain.ProductMetadata;
import com.bringmycart.bmc.savedproducts.domain.SavedProduct;
import com.bringmycart.bmc.savedproducts.infrastructure.SavedProductRepository;
import com.bringmycart.bmc.savedproducts.application.ProductMetadataProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtConfiguration.class)
class ManualMetadataRefreshIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserAccountRepository users;
    @Autowired SavedProductRepository savedProducts;
    @Autowired ProductEnrichmentStateService stateService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @MockBean ProductMetadataProvider metadataProvider;

    @Test
    void ownerCanRefreshCompletedMetadataWithoutWaitingForTheProvider() throws Exception {
        UserAccount user = createUser();
        SavedProduct product = completedProduct(user, oldMetadata());
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch allowProviderReturn = new CountDownLatch(1);
        when(metadataProvider.fetch(anyString())).thenAnswer(invocation -> {
            providerStarted.countDown();
            assertThat(allowProviderReturn.await(5, TimeUnit.SECONDS)).isTrue();
            return refreshedMetadata();
        });

        mockMvc.perform(refresh(product.getId(), user))
            .andExpect(status().isAccepted());

        assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(savedProducts.findById(product.getId()).orElseThrow().getEnrichmentStatus())
            .isEqualTo(EnrichmentStatus.PROCESSING);
        allowProviderReturn.countDown();

        SavedProduct refreshed = awaitStatus(product.getId(), EnrichmentStatus.COMPLETED);
        assertThat(refreshed.getTitle()).isEqualTo("Refreshed title");
        assertThat(refreshed.getFinalPrice()).isEqualByComparingTo("6999");
        assertThat(refreshed.getProviderFetchedAt()).isEqualTo(Instant.parse("2026-11-15T11:00:00Z"));
    }

    @Test
    void failedEnrichmentCanBeRetriedAndRefreshFailurePreservesExistingMetadata() throws Exception {
        UserAccount user = createUser();
        SavedProduct failed = failedProduct(user, EnrichmentFailureCategory.NOT_CONFIGURED);
        when(metadataProvider.fetch(anyString())).thenReturn(refreshedMetadata());

        mockMvc.perform(refresh(failed.getId(), user)).andExpect(status().isAccepted());
        assertThat(awaitStatus(failed.getId(), EnrichmentStatus.COMPLETED).getTitle()).isEqualTo("Refreshed title");

        SavedProduct completed = completedProduct(user, oldMetadata());
        when(metadataProvider.fetch(anyString())).thenThrow(new ProductMetadataProviderException(
            ProductMetadataProviderException.FailureType.INVALID_RESPONSE, "unsafe provider content"));
        mockMvc.perform(refresh(completed.getId(), user)).andExpect(status().isAccepted());

        SavedProduct refreshFailure = awaitStatus(completed.getId(), EnrichmentStatus.FAILED);
        assertThat(refreshFailure.getTitle()).isEqualTo("Old title");
        assertThat(refreshFailure.getFinalPrice()).isEqualByComparingTo("7999");
        assertThat(refreshFailure.getMetadataSource()).isEqualTo("AXESSO");
        assertThat(refreshFailure.getEnrichmentErrorCategory()).isEqualTo(EnrichmentFailureCategory.INVALID_RESPONSE.name());
        mockMvc.perform(refresh(completed.getId(), user))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("refresh_cooldown"));
    }

    @Test
    void refreshIsOwnerScopedAndUsesControlledInProgressAndCooldownConflicts() throws Exception {
        UserAccount owner = createUser();
        UserAccount otherUser = createUser();
        SavedProduct product = completedProduct(owner, oldMetadata());
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch allowProviderReturn = new CountDownLatch(1);
        when(metadataProvider.fetch(anyString())).thenAnswer(invocation -> {
            providerStarted.countDown();
            assertThat(allowProviderReturn.await(5, TimeUnit.SECONDS)).isTrue();
            return refreshedMetadata();
        });

        mockMvc.perform(refresh(product.getId(), otherUser))
            .andExpect(status().isNotFound());
        mockMvc.perform(refresh(product.getId(), owner)).andExpect(status().isAccepted());
        assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        mockMvc.perform(refresh(product.getId(), owner))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("enrichment_in_progress"));
        allowProviderReturn.countDown();
        awaitStatus(product.getId(), EnrichmentStatus.COMPLETED);

        mockMvc.perform(refresh(product.getId(), owner))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("refresh_cooldown"));
    }

    @Test
    void concurrentManualRefreshesStartOnlyOneProviderCallAndDeletionRemainsSafe() throws Exception {
        UserAccount user = createUser();
        SavedProduct product = completedProduct(user, oldMetadata());
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch allowProviderReturn = new CountDownLatch(1);
        when(metadataProvider.fetch(anyString())).thenAnswer(invocation -> {
            providerStarted.countDown();
            assertThat(allowProviderReturn.await(5, TimeUnit.SECONDS)).isTrue();
            return refreshedMetadata();
        });

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<java.util.concurrent.Future<Integer>> responses = executor.invokeAll(List.of(
                () -> refreshStatus(product.getId(), user),
                () -> refreshStatus(product.getId(), user)));
            List<Integer> statuses = responses.stream().map(result -> {
                try {
                    return result.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).sorted().toList();
            assertThat(statuses).containsExactly(202, 409);
        }
        assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        verify(metadataProvider, times(1)).fetch("B0D2MW6L54");

        savedProducts.deleteById(product.getId());
        allowProviderReturn.countDown();
        waitUntilDeleted(product.getId());
        assertThat(savedProducts.findById(product.getId())).isEmpty();
    }

    @Test
    void refreshRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/saved-products/{id}/refresh", UUID.randomUUID()).with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listUsesSafeMetadataContractAndMalformedRefreshIdsReturnValidationError() throws Exception {
        UserAccount user = createUser();
        completedProduct(user, oldMetadata());

        mockMvc.perform(get("/api/v1/saved-products").header("Authorization", "Bearer " + jwtService.issue(user).value()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].title").value("Old title"))
            .andExpect(jsonPath("$.content[0].initialPrice").value(9999))
            .andExpect(jsonPath("$.content[0].finalPrice").value(7999))
            .andExpect(jsonPath("$.content[0].discountPercentage").value(20.00))
            .andExpect(jsonPath("$.content[0].providerFetchedAt").value("2026-11-15T10:00:00Z"))
            .andExpect(jsonPath("$.content[0].reviewSummary").doesNotExist());

        mockMvc.perform(post("/api/v1/saved-products/not-a-uuid/refresh")
                .with(csrf())
                .header("Authorization", "Bearer " + jwtService.issue(user).value()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    private SavedProduct completedProduct(UserAccount user, ProductMetadata metadata) {
        SavedProduct product = savedProducts.saveAndFlush(new SavedProduct(user, "B0D2MW6L54",
            "https://www.amazon.in/dp/B0D2MW6L54", "https://www.amazon.in/dp/B0D2MW6L54"));
        stateService.claim(product.getId());
        stateService.complete(product.getId(), metadata);
        return savedProducts.findById(product.getId()).orElseThrow();
    }

    private SavedProduct failedProduct(UserAccount user, EnrichmentFailureCategory category) {
        SavedProduct product = savedProducts.saveAndFlush(new SavedProduct(user, "B0D7SJXCPP",
            "https://www.amazon.in/dp/B0D7SJXCPP", "https://www.amazon.in/dp/B0D7SJXCPP"));
        stateService.claim(product.getId());
        stateService.fail(product.getId(), category, false);
        return savedProducts.findById(product.getId()).orElseThrow();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refresh(UUID id, UserAccount user) {
        return post("/api/v1/saved-products/{id}/refresh", id)
            .with(csrf())
            .header("Authorization", "Bearer " + jwtService.issue(user).value())
            .contentType(MediaType.APPLICATION_JSON);
    }

    private int refreshStatus(UUID id, UserAccount user) throws Exception {
        return mockMvc.perform(refresh(id, user)).andReturn().getResponse().getStatus();
    }

    private UserAccount createUser() {
        return users.saveAndFlush(new UserAccount(
            "refresh-" + UUID.randomUUID() + "@example.com",
            passwordEncoder.encode("StrongPassword123!"), Role.CUSTOMER));
    }

    private SavedProduct awaitStatus(UUID id, EnrichmentStatus expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            SavedProduct product = savedProducts.findById(id).orElseThrow();
            if (product.getEnrichmentStatus() == expected) {
                return product;
            }
            sleepBriefly();
        }
        throw new AssertionError("Timed out waiting for " + expected);
    }

    private void waitUntilDeleted(UUID id) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (savedProducts.findById(id).isEmpty()) {
                return;
            }
            sleepBriefly();
        }
        throw new AssertionError("Saved product was not deleted");
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private ProductMetadata oldMetadata() {
        return new ProductMetadata("B0D2MW6L54", null, "Old title", "Old brand", "https://images.example/old.jpg",
            new BigDecimal("9999"), new BigDecimal("7999"), "INR", new BigDecimal("20.00"),
            new BigDecimal("4.0"), 10L, "In stock", Instant.parse("2026-11-15T10:00:00Z"),
            null, List.of(), List.of(), List.of(), null, null, "AXESSO");
    }

    private ProductMetadata refreshedMetadata() {
        return new ProductMetadata("B0D2MW6L54", null, "Refreshed title", "New brand", "https://images.example/new.jpg",
            new BigDecimal("9999"), new BigDecimal("6999"), "INR", new BigDecimal("30.00"),
            new BigDecimal("4.5"), 20L, "In stock", Instant.parse("2026-11-15T11:00:00Z"));
    }
}

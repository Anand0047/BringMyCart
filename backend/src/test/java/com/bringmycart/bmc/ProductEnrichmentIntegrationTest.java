package com.bringmycart.bmc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.bringmycart.bmc.identity.domain.Role;
import com.bringmycart.bmc.identity.domain.UserAccount;
import com.bringmycart.bmc.identity.infrastructure.UserAccountRepository;
import com.bringmycart.bmc.savedproducts.application.ProductEnrichmentDispatcher;
import com.bringmycart.bmc.savedproducts.application.ProductEnrichmentProperties;
import com.bringmycart.bmc.savedproducts.application.ProductEnrichmentStateService;
import com.bringmycart.bmc.savedproducts.application.ProductMetadataProvider;
import com.bringmycart.bmc.savedproducts.application.SavedProductService;
import com.bringmycart.bmc.savedproducts.domain.EnrichmentFailureCategory;
import com.bringmycart.bmc.savedproducts.domain.EnrichmentStatus;
import com.bringmycart.bmc.savedproducts.domain.ProductMetadata;
import com.bringmycart.bmc.savedproducts.domain.SavedProduct;
import com.bringmycart.bmc.savedproducts.infrastructure.SavedProductRepository;
import com.bringmycart.bmc.savedproducts.infrastructure.brightdata.ProductMetadataProviderException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtConfiguration.class)
class ProductEnrichmentIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired SavedProductService savedProductService;
    @Autowired SavedProductRepository savedProducts;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ProductEnrichmentDispatcher dispatcher;
    @Autowired ProductEnrichmentStateService stateService;
    @Autowired ProductEnrichmentProperties properties;
    @MockBean ProductMetadataProvider metadataProvider;

    @AfterEach
    void resetRecoveryProperties() {
        properties.setRecoveryEnabled(false);
        properties.setMaxAttempts(2);
    }

    @Test
    void savesImmediatelyAfterCommitThenPersistsSuccessfullyEnrichedMetadata() throws Exception {
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch allowProviderReturn = new CountDownLatch(1);
        UserAccount user = createUser();
        when(metadataProvider.fetch(anyString())).thenAnswer(invocation -> {
            assertThat(savedProducts.existsByUser_IdAndAsin(user.getId(), "B0D2MW6L54")).isTrue();
            providerStarted.countDown();
            assertThat(allowProviderReturn.await(5, TimeUnit.SECONDS)).isTrue();
            return metadata();
        });

        SavedProduct saved = savedProductService.save(user.getId(), "https://www.amazon.in/dp/B0D2MW6L54");

        assertThat(saved.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.PENDING);
        assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(savedProducts.findById(saved.getId())).get()
            .extracting(SavedProduct::getEnrichmentStatus).isEqualTo(EnrichmentStatus.PROCESSING);

        allowProviderReturn.countDown();
        SavedProduct completed = awaitStatus(saved.getId(), EnrichmentStatus.COMPLETED);
        assertThat(completed.getTitle()).isEqualTo("Example variant");
        assertThat(completed.getParentAsin()).isEqualTo("B0PARENT01");
        assertThat(completed.getInitialPrice()).isEqualByComparingTo("9999");
        assertThat(completed.getFinalPrice()).isEqualByComparingTo("7999");
        assertThat(completed.getDiscountPercentage()).isEqualByComparingTo("20.00");
        assertThat(completed.getMetadataFetchedAt()).isNotNull();
        assertThat(completed.getProviderFetchedAt()).isEqualTo(Instant.parse("2026-11-15T10:00:00Z"));
    }

    @Test
    void providerFailurePreservesProductAndStoresOnlySafeFailureCategory() {
        UserAccount user = createUser();
        when(metadataProvider.fetch(anyString())).thenThrow(new ProductMetadataProviderException(
            ProductMetadataProviderException.FailureType.AUTHENTICATION, "credential value must never be persisted"));

        SavedProduct saved = savedProductService.save(user.getId(), "https://www.amazon.in/dp/B0D2MW6L54");

        SavedProduct failed = awaitStatus(saved.getId(), EnrichmentStatus.FAILED);
        assertThat(failed.getEnrichmentErrorCategory()).isEqualTo(EnrichmentFailureCategory.AUTHENTICATION.name());
        assertThat(failed.getTitle()).isNull();
        assertThat(failed.getEnrichmentNextAttemptAt()).isNull();
    }

    @Test
    void duplicateDispatchesClaimOnlyOneJobAndDeletionDuringProcessingDoesNotRecreateIt() throws Exception {
        UserAccount user = createUser();
        SavedProduct saved = savedProducts.saveAndFlush(new SavedProduct(user, "B0D2MW6L54",
            "https://www.amazon.in/dp/B0D2MW6L54", "https://www.amazon.in/dp/B0D2MW6L54"));
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch allowProviderReturn = new CountDownLatch(1);
        when(metadataProvider.fetch(anyString())).thenAnswer(invocation -> {
            providerStarted.countDown();
            assertThat(allowProviderReturn.await(5, TimeUnit.SECONDS)).isTrue();
            return metadata();
        });

        dispatcher.dispatch(saved.getId());
        dispatcher.dispatch(saved.getId());
        assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        verify(metadataProvider, times(1)).fetch("B0D2MW6L54");

        savedProducts.deleteById(saved.getId());
        allowProviderReturn.countDown();
        waitUntilDeleted(saved.getId());
        assertThat(savedProducts.findById(saved.getId())).isEmpty();
    }

    @Test
    void boundedAttemptsDoNotAutomaticallyRetryPermanentOrExhaustedFailures() {
        UserAccount user = createUser();
        SavedProduct saved = savedProducts.saveAndFlush(new SavedProduct(user, "B0D2MW6L54",
            "https://www.amazon.in/dp/B0D2MW6L54", "https://www.amazon.in/dp/B0D2MW6L54"));
        properties.setRecoveryEnabled(true);
        properties.setMaxAttempts(1);

        assertThat(stateService.claim(saved.getId())).isPresent();
        stateService.fail(saved.getId(), EnrichmentFailureCategory.TIMEOUT, true);
        SavedProduct failed = savedProducts.findById(saved.getId()).orElseThrow();

        assertThat(failed.getEnrichmentStatus()).isEqualTo(EnrichmentStatus.FAILED);
        assertThat(failed.getEnrichmentNextAttemptAt()).isNull();
        assertThat(stateService.claim(saved.getId())).isEmpty();
    }

    private UserAccount createUser() {
        return users.saveAndFlush(new UserAccount(
            "enrichment-" + UUID.randomUUID() + "@example.com",
            passwordEncoder.encode("StrongPassword123!"), Role.CUSTOMER));
    }

    private SavedProduct awaitStatus(UUID id, EnrichmentStatus expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            SavedProduct current = savedProducts.findById(id).orElseThrow();
            if (current.getEnrichmentStatus() == expected) {
                return current;
            }
            sleepBriefly();
        }
        throw new AssertionError("Timed out waiting for status " + expected);
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

    private ProductMetadata metadata() {
        return new ProductMetadata("B0D2MW6L54", "B0PARENT01", "Example variant", "Example brand",
            "https://images.example/product.jpg", new BigDecimal("9999"), new BigDecimal("7999"), "INR",
            new BigDecimal("20.00"), new BigDecimal("4.2"), 52L, "In stock",
            Instant.parse("2026-11-15T10:00:00Z"));
    }
}

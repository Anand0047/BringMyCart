package com.bringmycart.bmc;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bringmycart.bmc.identity.domain.Role;
import com.bringmycart.bmc.identity.domain.UserAccount;
import com.bringmycart.bmc.identity.infrastructure.UserAccountRepository;
import com.bringmycart.bmc.identity.security.JwtService;
import com.bringmycart.bmc.savedproducts.application.ProductEnrichmentDispatcher;
import com.bringmycart.bmc.savedproducts.infrastructure.SavedProductRepository;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtConfiguration.class)
class SavedProductIntegrationTest {
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
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @MockBean ProductEnrichmentDispatcher enrichmentDispatcher;

    @Test
    void savesExactAsinAndReturnsCanonicalUrlWithoutWaitingForMetadata() throws Exception {
        UserAccount user = createUser();

        mockMvc.perform(post("/api/v1/saved-products")
                .with(csrf())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://www.amazon.in/dp/b0d2mw6l54?th=1&psc=1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.asin").value("B0D2MW6L54"))
            .andExpect(jsonPath("$.originalUrl").value("https://www.amazon.in/dp/b0d2mw6l54?th=1&psc=1"))
            .andExpect(jsonPath("$.canonicalUrl").value("https://www.amazon.in/dp/B0D2MW6L54"))
            .andExpect(jsonPath("$.enrichmentStatus").value("PENDING"));

        assertThat(savedProducts.existsByUser_IdAndAsin(user.getId(), "B0D2MW6L54")).isTrue();
    }

    @Test
    void rejectsInvalidUrlsAndDuplicateSavesForTheSameCustomer() throws Exception {
        UserAccount user = createUser();

        mockMvc.perform(post("/api/v1/saved-products")
                .with(csrf())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://amazon.in.attacker.example/dp/B0D2MW6L54\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("invalid_product_url"));

        String request = "{\"url\":\"https://www.amazon.in/gp/product/B0D7SJXCPP\"}";
        String sameAsinWithTitleAndReferral = "{\"url\":\"https://www.amazon.in/Example-Product/dp/B0D7SJXCPP/ref=sr_1_1?psc=1\"}";
        mockMvc.perform(post("/api/v1/saved-products").with(csrf()).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/saved-products").with(csrf()).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(sameAsinWithTitleAndReferral))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("duplicate_saved_product"));
    }

    @Test
    void rejectsMalformedSaveBodiesWithTheStandardValidationResponse() throws Exception {
        UserAccount user = createUser();
        mockMvc.perform(post("/api/v1/saved-products").with(csrf()).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"url\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void isolatesListsAndDeletionToTheAuthenticatedOwner() throws Exception {
        UserAccount owner = createUser();
        UserAccount otherUser = createUser();
        mockMvc.perform(post("/api/v1/saved-products")
                .with(csrf())
                .header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://www.amazon.in/dp/B0D2MW6L54\"}"))
            .andExpect(status().isCreated());
        UUID savedProductId = savedProducts.findAllByUser_Id(owner.getId(), org.springframework.data.domain.Pageable.unpaged())
            .getContent().getFirst().getId();

        mockMvc.perform(get("/api/v1/saved-products").header("Authorization", bearer(otherUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20));
        mockMvc.perform(delete("/api/v1/saved-products/{id}", savedProductId)
                .with(csrf())
                .header("Authorization", bearer(otherUser)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("saved_product_not_found"));

        assertThat(savedProducts.findByIdAndUser_Id(savedProductId, owner.getId())).isPresent();
        mockMvc.perform(delete("/api/v1/saved-products/{id}", savedProductId)
                .with(csrf())
                .header("Authorization", bearer(owner)))
            .andExpect(status().isNoContent());
        assertThat(savedProducts.findById(savedProductId)).isEmpty();
    }

    @Test
    void allowsDifferentCustomersToSaveTheSameExactAsinIndependently() throws Exception {
        UserAccount firstUser = createUser();
        UserAccount secondUser = createUser();
        String request = "{\"url\":\"https://www.amazon.in/dp/B0D2MW6L54\"}";

        mockMvc.perform(post("/api/v1/saved-products").with(csrf()).header("Authorization", bearer(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://www.amazon.in/Example-Product/dp/B0D2MW6L54/ref=product_page?psc=1\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/saved-products").with(csrf()).header("Authorization", bearer(secondUser))
                .contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isCreated());

        assertThat(savedProducts.existsByUser_IdAndAsin(firstUser.getId(), "B0D2MW6L54")).isTrue();
        assertThat(savedProducts.existsByUser_IdAndAsin(secondUser.getId(), "B0D2MW6L54")).isTrue();
    }

    @Test
    void savesFlipkartVariantWithValidatedExtensionFallbackAndDeduplicatesPerUser() throws Exception {
        UserAccount firstUser = createUser();
        UserAccount secondUser = createUser();
        String url = "https://www.flipkart.com/phone/p/itm1234567890?pid=MOBABCDE1234&lid=tracking";
        String request = "{\"url\":\"" + url + "\",\"metadata\":{\"title\":\" Phone \","
            + "\"brand\":\"Example\",\"imageUrl\":\"https://cdn.example.test/phone.jpg\","
            + "\"price\":7999,\"currency\":\"INR\",\"availability\":\"IN_STOCK\"}}";

        mockMvc.perform(post("/api/v1/saved-products").with(csrf()).header("Authorization", bearer(firstUser))
                .contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.retailer").value("FLIPKART"))
            .andExpect(jsonPath("$.retailerProductId").value("MOBABCDE1234"))
            .andExpect(jsonPath("$.asin").doesNotExist())
            .andExpect(jsonPath("$.canonicalUrl").value("https://www.flipkart.com/phone/p/itm1234567890?pid=MOBABCDE1234"))
            .andExpect(jsonPath("$.title").value("Phone"))
            .andExpect(jsonPath("$.metadataSource").value("EXTENSION"));

        mockMvc.perform(post("/api/v1/saved-products").with(csrf()).header("Authorization", bearer(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://flipkart.com/phone/p/itm1234567890?pid=MOBABCDE1234&affid=other\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("duplicate_saved_product"));

        mockMvc.perform(post("/api/v1/saved-products").with(csrf()).header("Authorization", bearer(secondUser))
                .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"" + url + "\"}"))
            .andExpect(status().isCreated());
        assertThat(savedProducts.existsByUser_IdAndRetailerAndRetailerProductId(firstUser.getId(),
            com.bringmycart.bmc.retailer.Retailer.FLIPKART, "MOBABCDE1234")).isTrue();
        assertThat(savedProducts.existsByUser_IdAndRetailerAndRetailerProductId(secondUser.getId(),
            com.bringmycart.bmc.retailer.Retailer.FLIPKART, "MOBABCDE1234")).isTrue();
    }

    @Test
    void savesMyntraNumericIdAndRejectsOtherRetailerUrlSpoofing() throws Exception {
        UserAccount user = createUser();
        mockMvc.perform(post("/api/v1/saved-products").with(csrf()).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://www.myntra.com/shirts/brand/12345678/buy?utm_source=tracking\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.retailer").value("MYNTRA"))
            .andExpect(jsonPath("$.retailerProductId").value("12345678"))
            .andExpect(jsonPath("$.canonicalUrl").value("https://www.myntra.com/shirts/brand/12345678/buy"));

        mockMvc.perform(post("/api/v1/saved-products").with(csrf()).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://www.flipkart.com.attacker.test/p/itm1234567890?pid=MOBABCDE1234\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthenticationForSavedProductResources() throws Exception {
        mockMvc.perform(get("/api/v1/saved-products"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listCapsRequestedPageSizeAndUsesTheStableServerSort() throws Exception {
        UserAccount user = createUser();
        mockMvc.perform(post("/api/v1/saved-products").with(csrf()).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://www.amazon.in/dp/B0D2MW6L54\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/saved-products?size=500&sort=title,asc").header("Authorization", bearer(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size").value(100))
            .andExpect(jsonPath("$.content[0].asin").value("B0D2MW6L54"));
    }

    @Test
    void concurrentDuplicateSavesReturnCreatedAndControlledConflict() throws Exception {
        UserAccount user = createUser();
        String request = "{\"url\":\"https://www.amazon.in/dp/B0D2MW6L54\"}";

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<java.util.concurrent.Future<Integer>> results = executor.invokeAll(List.of(
                () -> saveStatus(user, request),
                () -> saveStatus(user, request)));
            List<Integer> statuses = results.stream().map(result -> {
                try {
                    return result.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).sorted().toList();

            assertThat(statuses).containsExactly(201, 409);
        }
        assertThat(savedProducts.findAllByUser_Id(user.getId(), org.springframework.data.domain.Pageable.unpaged())
            .getTotalElements()).isEqualTo(1);
    }

    private UserAccount createUser() {
        return users.saveAndFlush(new UserAccount(
            "customer-" + UUID.randomUUID() + "@example.com",
            passwordEncoder.encode("StrongPassword123!"),
            Role.CUSTOMER));
    }

    private String bearer(UserAccount user) {
        return "Bearer " + jwtService.issue(user).value();
    }

    private int saveStatus(UserAccount user, String request) throws Exception {
        return mockMvc.perform(post("/api/v1/saved-products")
                .with(csrf())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andReturn().getResponse().getStatus();
    }
}

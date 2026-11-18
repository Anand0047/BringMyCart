package com.bringmycart.bmc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockCookie;
import com.bringmycart.bmc.savedproducts.application.ProductEnrichmentDispatcher;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@Import(TestJwtConfiguration.class)
class ExtensionAuthIntegrationTest {
    private static final String CLIENT = "bmc-chrome-dev";
    private static final String VERIFIER = "valid-verifier-valid-verifier-12345678901234567890";
    private static final String CALLBACK = "https://mlogpmpajiokfmjnlmehmpgphbbflpem.chromiumapp.org/bmc-auth";
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @Container static PostgreSQLContainer<?> postgres = POSTGRES;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("bmc.auth.extension-clients.bmc-chrome-dev", () -> CALLBACK);
        registry.add("bmc.auth.allowed-cors-origins", () -> "http://localhost:5173,chrome-extension://mlogpmpajiokfmjnlmehmpgphbbflpem");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.boot.test.mock.mockito.MockBean ProductEnrichmentDispatcher enrichmentDispatcher;

    @Test
    void websiteSessionAuthorizesExtensionAndCodeIsSingleUse() throws Exception {
        String websiteToken = websiteAccessToken();
        mvc.perform(post("/api/v1/extension-auth/authorize").header("Authorization", "Bearer " + websiteToken)
                .contentType(MediaType.APPLICATION_JSON).content(authRequest(CALLBACK))
                .with(request -> {
                    org.assertj.core.api.Assertions.assertThat(request.getServletPath()).isEmpty();
                    return request;
                }))
            .andExpect(status().isForbidden());
        String code = authorize(websiteToken, CALLBACK);
        JsonNode token = exchange(code, VERIFIER, state(), CALLBACK, 200);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token.get("accessToken").asText()))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/identity/me").header("Authorization", "Bearer " + token.get("accessToken").asText()))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.fullName").value("Extension Customer"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.userId").value(token.get("userId").asText()));
        exchange(code, VERIFIER, state(), CALLBACK, 401);
    }

    @Test
    void bearerProductApiRemainsCsrfExemptWhileAuthorizationEndpointDoesNot() throws Exception {
        String websiteToken = websiteAccessToken();
        mvc.perform(post("/api/v1/saved-products/{id}/refresh", UUID.randomUUID())
                .header("Authorization", "Bearer " + websiteToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void invalidVerifierConsumesCodeAndUnregisteredCallbackIsRejected() throws Exception {
        String websiteToken = websiteAccessToken();
        String code = authorize(websiteToken, CALLBACK);
        exchange(code, "different-verifier-different-verifier-12345678901234567890", state(), CALLBACK, 401);
        exchange(code, VERIFIER, state(), CALLBACK, 401);
        mvc.perform(post("/api/v1/extension-auth/authorize").header("Authorization", "Bearer " + websiteToken)
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(authRequest("https://attacker.example/callback")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void stateRedirectAndExpirationAreValidatedOnTheServer() throws Exception {
        String websiteToken = websiteAccessToken();
        String stateCode = authorize(websiteToken, CALLBACK);
        exchange(stateCode, VERIFIER, "different-state-012345678901234567890123", CALLBACK, 401);
        exchange(stateCode, VERIFIER, state(), CALLBACK, 401);

        String redirectCode = authorize(websiteToken, CALLBACK);
        exchange(redirectCode, VERIFIER, state(), "https://attacker.example/callback", 401);

        String expiredCode = authorize(websiteToken, CALLBACK);
        jdbc.update("update extension_authorization_codes set expires_at=? where code_hash=?",
            Timestamp.from(Instant.now().minusSeconds(1)), hash(expiredCode));
        exchange(expiredCode, VERIFIER, state(), CALLBACK, 401);
    }

    @Test
    void refreshRotationReplayRevokesSessionAndAccessJwt() throws Exception {
        String code = authorize(websiteAccessToken(), CALLBACK);
        JsonNode initial = exchange(code, VERIFIER, state(), CALLBACK, 200);
        JsonNode rotated = refresh(initial.get("refreshToken").asText(), 200);
        refresh(initial.get("refreshToken").asText(), 401);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + rotated.get("accessToken").asText()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void extensionLogoutRevokesItsSession() throws Exception {
        String code = authorize(websiteAccessToken(), CALLBACK);
        JsonNode session = exchange(code, VERIFIER, state(), CALLBACK, 200);
        mvc.perform(post("/api/v1/extension-auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + session.get("refreshToken").asText() + "\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + session.get("accessToken").asText()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void websiteLogoutInvalidatesExtensionSessionAndAnotherAccountCanSaveTheSameAsin() throws Exception {
        WebsiteSession accountA = websiteSession("account-a-" + UUID.randomUUID() + "@example.com");
        String extensionCodeA = authorize(accountA.accessToken(), CALLBACK);
        String pendingCodeFromAccountA = authorize(accountA.accessToken(), CALLBACK);
        JsonNode extensionA = exchange(extensionCodeA, VERIFIER, state(), CALLBACK, 200);

        mvc.perform(post("/api/v1/saved-products").header("Authorization", "Bearer " + extensionA.get("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://www.amazon.in/dp/B0D2MW6L54\"}"))
            .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/auth/logout")
                .with(csrf().asHeader())
                .cookie(new MockCookie("bmc_refresh", accountA.refreshToken())))
            .andExpect(status().isNoContent());

        exchange(pendingCodeFromAccountA, VERIFIER, state(), CALLBACK, 401);

        mvc.perform(get("/api/v1/identity/me").header("Authorization", "Bearer " + extensionA.get("accessToken").asText()))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/saved-products").header("Authorization", "Bearer " + extensionA.get("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://www.amazon.in/dp/B0D2MW6L54\"}"))
            .andExpect(status().isUnauthorized());
        refresh(extensionA.get("refreshToken").asText(), 401);

        WebsiteSession accountB = websiteSession("account-b-" + UUID.randomUUID() + "@example.com");
        String extensionCodeB = authorize(accountB.accessToken(), CALLBACK);
        JsonNode extensionB = exchange(extensionCodeB, VERIFIER, state(), CALLBACK, 200);
        mvc.perform(get("/api/v1/identity/me").header("Authorization", "Bearer " + extensionB.get("accessToken").asText()))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.email").value(accountB.email()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.fullName").value("Extension Customer"));
        mvc.perform(post("/api/v1/saved-products").header("Authorization", "Bearer " + extensionB.get("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://www.amazon.in/Example-Product/dp/B0D2MW6L54/ref=cart?psc=1\"}"))
            .andExpect(status().isCreated())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.asin").value("B0D2MW6L54"));
    }

    @Test
    void successfulWebsiteAccountSwitchInvalidatesOldExtensionSessionEvenIfSignalIsMissed() throws Exception {
        WebsiteSession accountA = websiteSession("switch-a-" + UUID.randomUUID() + "@example.com");
        JsonNode extensionA = exchange(authorize(accountA.accessToken(), CALLBACK), VERIFIER, state(), CALLBACK, 200);
        String accountBEmail = "switch-b-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registration(accountBEmail))).andExpect(status().isCreated());

        MvcResult accountBLogin = mvc.perform(post("/api/v1/auth/login")
                .cookie(new MockCookie("bmc_refresh", accountA.refreshToken()))
                .contentType(MediaType.APPLICATION_JSON).content(credentials(accountBEmail)))
            .andExpect(status().isOk()).andReturn();
        JsonNode accountBAuth = mapper.readTree(accountBLogin.getResponse().getContentAsString());
        mvc.perform(get("/api/v1/identity/me").header("Authorization", "Bearer " + extensionA.get("accessToken").asText()))
            .andExpect(status().isUnauthorized());

        JsonNode extensionB = exchange(authorize(accountBAuth.get("accessToken").asText(), CALLBACK),
            VERIFIER, state(), CALLBACK, 200);
        mvc.perform(get("/api/v1/identity/me").header("Authorization", "Bearer " + extensionB.get("accessToken").asText()))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.email").value(accountBEmail));
    }

    @Test
    void extensionOriginPreflightIsExplicitlyAllowed() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/saved-products")
                .header("Origin", "chrome-extension://mlogpmpajiokfmjnlmehmpgphbbflpem")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
                "Access-Control-Allow-Origin", "chrome-extension://mlogpmpajiokfmjnlmehmpgphbbflpem"));
    }

    private String websiteAccessToken() throws Exception {
        String email = "extension-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registration(email))).andExpect(status().isCreated());
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("accessToken").asText();
    }

    private WebsiteSession websiteSession(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registration(email))).andExpect(status().isCreated());
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email))).andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        return new WebsiteSession(mapper.readTree(body).get("accessToken").asText(), email,
            responseCookieValue(result, "bmc_refresh"));
    }

    private String responseCookieValue(MvcResult result, String cookieName) {
        String cookie = result.getResponse().getHeaders("Set-Cookie").stream()
            .filter(header -> header.startsWith(cookieName + "="))
            .findFirst()
            .orElseThrow(() -> new AssertionError(cookieName + " cookie is missing from the response"));
        return cookie.substring(cookieName.length() + 1, cookie.indexOf(';'));
    }

    private String authorize(String accessToken, String callback) throws Exception {
        String body = mvc.perform(post("/api/v1/extension-auth/authorize").header("Authorization", "Bearer " + accessToken)
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(authRequest(callback)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return java.net.URI.create(mapper.readTree(body).get("redirectUrl").asText()).getQuery().split("[&=]")[1];
    }

    private JsonNode exchange(String code, String verifier, String flowState, String callback, int expectedStatus) throws Exception {
        String body = "{\"code\":\"" + code + "\",\"codeVerifier\":\"" + verifier + "\",\"state\":\"" + flowState
            + "\",\"clientId\":\"" + CLIENT + "\",\"redirectUri\":\"" + callback + "\"}";
        var result = mvc.perform(post("/api/v1/extension-auth/token").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is(expectedStatus)).andReturn();
        return expectedStatus == 200 ? mapper.readTree(result.getResponse().getContentAsString()) : mapper.createObjectNode();
    }

    private JsonNode refresh(String refreshToken, int expectedStatus) throws Exception {
        var result = mvc.perform(post("/api/v1/extension-auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().is(expectedStatus)).andReturn();
        return expectedStatus == 200 ? mapper.readTree(result.getResponse().getContentAsString()) : mapper.createObjectNode();
    }

    private String authRequest(String callback) throws Exception {
        return mapper.writeValueAsString(java.util.Map.of("clientId", CLIENT, "redirectUri", callback,
            "state", state(), "codeChallenge", challenge(VERIFIER)));
    }
    private String credentials(String email) { return "{\"email\":\"" + email + "\",\"password\":\"StrongPassword123!\"}"; }
    private String registration(String email) { return "{\"fullName\":\"Extension Customer\",\"email\":\"" + email + "\",\"password\":\"StrongPassword123!\"}"; }
    private String state() { return "state-0123456789-abcdefghijklmnopqrstuvwxy"; }
    private String challenge(String verifier) throws Exception { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8))); }
    private String hash(String value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    private record WebsiteSession(String accessToken, String email, String refreshToken) { }
}

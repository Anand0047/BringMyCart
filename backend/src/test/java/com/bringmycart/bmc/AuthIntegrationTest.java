package com.bringmycart.bmc;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bringmycart.bmc.identity.application.AuthService;
import com.bringmycart.bmc.identity.domain.Role;
import com.bringmycart.bmc.identity.domain.UserAccount;
import com.bringmycart.bmc.identity.infrastructure.RefreshTokenRepository;
import com.bringmycart.bmc.identity.infrastructure.UserAccountRepository;
import com.bringmycart.bmc.identity.security.AuthProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtConfiguration.class)
class AuthIntegrationTest {
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
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuthService authService;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired AuthProperties properties;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    void registrationPersistsNormalizedCustomerWithHashedPassword() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"StrongPassword123!\"}"))
            .andExpect(status().isCreated());
        UserAccount user = users.findByEmail(email).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(user.getPasswordHash()).doesNotContain("StrongPassword123!");
        assertThat(passwordEncoder.matches("StrongPassword123!", user.getPasswordHash())).isTrue();
    }

    @Test
    void registrationRejectsInvalidAndDuplicateEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"weak\"}"))
            .andExpect(status().isBadRequest());
        String email = uniqueEmail();
        register(email);
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, "StrongPassword123!")))
            .andExpect(status().isConflict());
    }

    @Test
    void loginIssuesAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
        String email = uniqueEmail();
        register(email);
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, "StrongPassword123!")))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("bmc_refresh"))
            .andExpect(cookie().httpOnly("bmc_refresh", true));
    }

    @Test
    void loginRejectsIncorrectCredentialsAndDisabledUsers() throws Exception {
        String email = uniqueEmail();
        register(email);
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, "WrongPassword123!"))).andExpect(status().isUnauthorized());
        UserAccount user = users.findByEmail(email).orElseThrow();
        user.setEnabled(false);
        users.saveAndFlush(user);
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, "StrongPassword123!"))).andExpect(status().isUnauthorized());
    }

    @Test
    void jwtAuthenticatesCustomerAndRejectsInvalidOrExpiredTokens() throws Exception {
        String email = uniqueEmail();
        register(email);
        String accessToken = accessTokenFromLogin(email);
        UUID userId = users.findByEmail(email).orElseThrow().getId();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.role").value("CUSTOMER"));
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized());
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(),
            JwtClaimsSet.builder().issuer(properties.getIssuer()).subject(UUID.randomUUID().toString())
                .audience(List.of(properties.getAudience())).issuedAt(Instant.now().minus(20, ChronoUnit.MINUTES))
                .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES)).id(UUID.randomUUID().toString())
                .claim("role", "CUSTOMER").build())).getTokenValue();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expired))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointRequiresAdminRole() throws Exception {
        String customer = uniqueEmail();
        register(customer);
        mockMvc.perform(get("/api/v1/identity/admin/ping").header("Authorization", "Bearer " + accessTokenFromLogin(customer)))
            .andExpect(status().isForbidden());
        String admin = uniqueEmail();
        users.saveAndFlush(new UserAccount(admin, passwordEncoder.encode("StrongPassword123!"), Role.ADMIN));
        mockMvc.perform(get("/api/v1/identity/admin/ping").header("Authorization", "Bearer " + accessTokenFromLogin(admin)))
            .andExpect(status().isOk());
    }

    @Test
    void refreshRotatesTokenAndDetectsReplay() throws Exception {
        String email = uniqueEmail();
        register(email);
        MvcResult login = login(email);
        String refresh = cookieValue(login, "bmc_refresh");
        String csrfToken = cookieValue(login, "XSRF-TOKEN");
        MvcResult rotation = mockMvc.perform(refreshRequest(refresh, csrfToken))
            .andExpect(status().isOk()).andReturn();
        String replacement = cookieValue(rotation, "bmc_refresh");
        assertThat(replacement).isNotEqualTo(refresh);
        UUID userId = users.findByEmail(email).orElseThrow().getId();
        UUID familyId = jdbcTemplate.queryForObject(
            "select family_id from refresh_tokens where user_id = ? order by created_at desc limit 1", UUID.class, userId);
        mockMvc.perform(refreshRequest(refresh, csrfToken))
            .andExpect(status().isUnauthorized());
        Integer activeTokens = jdbcTemplate.queryForObject(
            "select count(*) from refresh_tokens where family_id = ? and revoked_at is null", Integer.class, familyId);
        assertThat(activeTokens).isZero();
        mockMvc.perform(refreshRequest(replacement, csrfToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void sequentialBrowserBootstrapsCanRotateTheLatestRefreshCookieRepeatedly() throws Exception {
        String email = uniqueEmail();
        register(email);
        MvcResult login = login(email);
        String csrfToken = cookieValue(login, "XSRF-TOKEN");
        String refresh = cookieValue(login, "bmc_refresh");

        for (int bootstrap = 0; bootstrap < 3; bootstrap++) {
            MvcResult rotation = mockMvc.perform(refreshRequest(refresh, csrfToken))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("bmc_refresh", true))
                .andReturn();
            String cookieHeader = rotation.getResponse().getHeaders("Set-Cookie").stream()
                .filter(headerValue -> headerValue.startsWith("bmc_refresh="))
                .findFirst()
                .orElseThrow();
            assertThat(cookieHeader).contains("Path=/api/v1/auth", "SameSite=Strict");
            refresh = cookieValue(rotation, "bmc_refresh");
        }
    }

    @Test
    void refreshRequiresCsrfProtectionForCookieAuthentication() throws Exception {
        String email = uniqueEmail();
        register(email);
        MvcResult login = login(email);
        String refresh = cookieValue(login, "bmc_refresh");
        String csrfToken = cookieValue(login, "XSRF-TOKEN");
        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new MockCookie("bmc_refresh", refresh), new MockCookie("XSRF-TOKEN", csrfToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(refreshRequest(refresh, csrfToken, "incorrect-csrf-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void csrfInitializationIssuesCookieBeforeTheFirstCookieAuthenticatedRefresh() throws Exception {
        String email = uniqueEmail();
        register(email);
        MvcResult login = login(email);
        String refresh = cookieValue(login, "bmc_refresh");

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new MockCookie("bmc_refresh", refresh)))
            .andExpect(status().isForbidden());

        MvcResult csrfInitialization = mockMvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isNoContent())
            .andReturn();
        String csrfToken = cookieValue(csrfInitialization, "XSRF-TOKEN");
        String csrfCookie = csrfInitialization.getResponse().getHeaders("Set-Cookie").stream()
            .filter(headerValue -> headerValue.startsWith("XSRF-TOKEN="))
            .findFirst()
            .orElseThrow();
        // MockMvc's MockHttpServletResponse models Servlet Cookie attributes and
        // omits SameSite; verify that attribute on the real refresh ResponseCookie below.
        assertThat(csrfCookie).contains("Path=/");

        mockMvc.perform(refreshRequest(refresh, csrfToken))
            .andExpect(status().isOk());
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email);
        MvcResult login = login(email);
        String refresh = cookieValue(login, "bmc_refresh");
        String csrfToken = cookieValue(login, "XSRF-TOKEN");
        UUID tokenId = jdbcTemplate.queryForObject("select id from refresh_tokens order by created_at desc limit 1", UUID.class);
        jdbcTemplate.update("update refresh_tokens set expires_at = ? where id = ?",
            Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)), tokenId);
        mockMvc.perform(refreshRequest(refresh, csrfToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRefreshAllowsOnlyOneRotation() throws Exception {
        String email = uniqueEmail();
        register(email);
        String refresh = refreshFromLogin(email);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<java.util.concurrent.Future<Boolean>> results = executor.invokeAll(List.of(
                () -> refreshSucceeds(refresh),
                () -> refreshSucceeds(refresh)));
            long successes = results.stream().filter(result -> {
                try { return result.get(); } catch (Exception exception) { return false; }
            }).count();
            assertThat(successes).isEqualTo(1);
        }
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String email = uniqueEmail();
        register(email);
        MvcResult login = login(email);
        String refresh = cookieValue(login, "bmc_refresh");
        String csrfToken = cookieValue(login, "XSRF-TOKEN");
        mockMvc.perform(post("/api/v1/auth/logout")
                .cookie(new MockCookie("bmc_refresh", refresh), new MockCookie("XSRF-TOKEN", csrfToken))
                .header("X-XSRF-TOKEN", csrfToken))
            .andExpect(status().isNoContent());
        mockMvc.perform(refreshRequest(refresh, csrfToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRateLimitIsEnforced() throws Exception {
        String email = uniqueEmail();
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, "WrongPassword123!"))).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(credentials(email, "WrongPassword123!"))).andExpect(status().isTooManyRequests());
    }

    @Test
    void flywayMigrationCreatesIdentityTablesAndDatabaseConstraints() {
        Integer usersTable = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_name = 'users'", Integer.class);
        assertThat(usersTable).isEqualTo(1);
        Integer uniqueEmailConstraint = jdbcTemplate.queryForObject(
            "select count(*) from pg_constraint where conname = 'uk_users_email'", Integer.class);
        assertThat(uniqueEmailConstraint).isEqualTo(1);
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(credentials(email, "StrongPassword123!"))).andExpect(status().isCreated());
    }

    private String accessTokenFromLogin(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(credentials(email, "StrongPassword123!"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\\\"accessToken\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    private String refreshFromLogin(String email) throws Exception {
        return cookieValue(login(email), "bmc_refresh");
    }

    private MvcResult login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(credentials(email, "StrongPassword123!"))).andExpect(status().isOk()).andReturn();
    }

    private String cookieValue(MvcResult result, String cookieName) {
        String cookie = result.getResponse().getHeaders("Set-Cookie").stream()
            .filter(header -> header.startsWith(cookieName + "="))
            .findFirst()
            .orElseThrow(() -> new AssertionError(cookieName + " cookie is missing"));
        return cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refreshRequest(String refreshToken,
                                                                                                        String csrfToken) {
        return refreshRequest(refreshToken, csrfToken, csrfToken);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refreshRequest(String refreshToken,
                                                                                                        String csrfCookie,
                                                                                                        String csrfHeader) {
        return post("/api/v1/auth/refresh")
            .cookie(new MockCookie("bmc_refresh", refreshToken), new MockCookie("XSRF-TOKEN", csrfCookie))
            .header("X-XSRF-TOKEN", csrfHeader);
    }

    private String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String uniqueEmail() { return "user-" + UUID.randomUUID() + "@example.com"; }

    private boolean refreshSucceeds(String refresh) {
        try {
            authService.refresh(refresh);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}

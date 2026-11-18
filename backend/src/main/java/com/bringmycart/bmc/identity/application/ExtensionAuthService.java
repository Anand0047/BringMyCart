package com.bringmycart.bmc.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import com.bringmycart.bmc.identity.domain.UserAccount;
import com.bringmycart.bmc.identity.infrastructure.UserAccountRepository;
import com.bringmycart.bmc.identity.security.AuthProperties;
import com.bringmycart.bmc.identity.security.JwtService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import com.bringmycart.bmc.identity.exception.ApiException;

/** Owns the public-client authorization code and revocable extension-session lifecycle. */
@Service
public class ExtensionAuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int AUTHORIZATION_CODE_SECONDS = 120;
    private final JdbcTemplate jdbc;
    private final UserAccountRepository users;
    private final AuthProperties properties;
    private final JwtService jwtService;

    public ExtensionAuthService(JdbcTemplate jdbc, UserAccountRepository users, AuthProperties properties, JwtService jwtService) {
        this.jdbc = jdbc;
        this.users = users;
        this.properties = properties;
        this.jwtService = jwtService;
    }

    @Transactional
    public String authorize(UUID userId, UUID websiteFamilyId, String clientId, String redirectUri, String state, String challenge) {
        requireClient(clientId, redirectUri);
        if (state == null || !state.matches("[A-Za-z0-9_-]{32,128}")
            || challenge == null || !challenge.matches("[A-Za-z0-9_-]{43}")) {
            throw invalidRequest();
        }
        UserAccount user = users.findById(userId).filter(UserAccount::isEnabled).orElseThrow(ExtensionAuthService::unauthorized);
        if (!websiteFamilyActive(user.getId(), websiteFamilyId, Instant.now())) throw unauthorized();
        String rawCode = randomToken(32);
        Instant now = Instant.now();
        jdbc.update("insert into extension_authorization_codes (id, code_hash, user_id, website_family_id, client_id, redirect_uri, state_hash, code_challenge, created_at, expires_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), hash(rawCode), user.getId(), websiteFamilyId, clientId, redirectUri, hash(state), challenge,
            timestamp(now), timestamp(now.plusSeconds(AUTHORIZATION_CODE_SECONDS)));
        return UriComponentsBuilder.fromUriString(redirectUri).queryParam("code", rawCode).queryParam("state", state).build().encode().toUriString();
    }

    /** Invalid exchange attempts consume a found code and return null so consumption commits. */
    @Transactional
    public ExtensionTokenResponse exchange(String rawCode, String verifier, String state, String clientId, String redirectUri) {
        if (rawCode == null || rawCode.length() > 200 || verifier == null || !verifier.matches("[A-Za-z0-9._~-]{43,128}")) return null;
        Map<String, Object> row = jdbc.query("select * from extension_authorization_codes where code_hash = ? for update", rs -> {
            if (!rs.next()) return null;
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("id", rs.getObject("id", UUID.class));
            result.put("user", rs.getObject("user_id", UUID.class));
            result.put("client", rs.getString("client_id"));
            result.put("redirect", rs.getString("redirect_uri"));
            result.put("state", rs.getString("state_hash"));
            result.put("challenge", rs.getString("code_challenge"));
            result.put("expires", rs.getTimestamp("expires_at").toInstant());
            result.put("websiteFamily", rs.getObject("website_family_id", UUID.class));
            result.put("consumed", rs.getTimestamp("consumed_at") != null);
            return result;
        }, hash(rawCode));
        if (row == null || (boolean) row.get("consumed")) return null;
        Instant now = Instant.now();
        jdbc.update("update extension_authorization_codes set consumed_at = ? where id = ? and consumed_at is null", timestamp(now), row.get("id"));
        String expectedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier));
        if (!((Instant) row.get("expires")).isAfter(now)
            || !safeEquals((String) row.get("client"), clientId)
            || !safeEquals((String) row.get("redirect"), redirectUri)
            || !safeEquals((String) row.get("state"), hash(state == null ? "" : state))
            || !safeEquals((String) row.get("challenge"), expectedChallenge)) return null;
        UserAccount user = users.findById((UUID) row.get("user")).filter(UserAccount::isEnabled).orElse(null);
        UUID websiteFamilyId = (UUID) row.get("websiteFamily");
        if (user == null || websiteFamilyId == null || !websiteFamilyActive(user.getId(), websiteFamilyId, now)) return null;
        UUID sessionId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        Instant sessionExpiry = now.plus(properties.getExtensionRefreshTokenDays(), ChronoUnit.DAYS);
        jdbc.update("insert into extension_sessions (id, user_id, website_family_id, client_id, created_at, expires_at) values (?, ?, ?, ?, ?, ?)",
            sessionId, user.getId(), websiteFamilyId, clientId, timestamp(now), timestamp(sessionExpiry));
        String refreshToken = randomToken(64);
        jdbc.update("insert into extension_refresh_tokens (id, session_id, family_id, token_hash, created_at, expires_at) values (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), sessionId, familyId, hash(refreshToken), timestamp(now), timestamp(sessionExpiry));
        JwtService.IssuedAccessToken access = jwtService.issueExtension(user, sessionId);
        return new ExtensionTokenResponse(access.value(), refreshToken, access.expiresAt().getEpochSecond(), user.getId(), user.getEmail(), user.getRole().name());
    }

    /** Returns null after committing replay revocation or rejecting an invalid token. */
    @Transactional
    public ExtensionTokenResponse rotate(String rawToken) {
        Map<String, Object> row = lockedRefresh(rawToken);
        if (row == null) return null;
        UUID tokenId = (UUID) row.get("id");
        UUID sessionId = (UUID) row.get("session");
        Instant now = Instant.now();
        if (!"".equals(row.get("revoked"))) {
            if (!"".equals(row.get("replacement"))) revokeSession(sessionId, now);
            return null;
        }
        if (!((Instant) row.get("expires")).isAfter(now) || !sessionActive(sessionId, now)) {
            revokeSession(sessionId, now);
            return null;
        }
        UserAccount user = users.findById((UUID) row.get("user")).filter(UserAccount::isEnabled).orElse(null);
        if (user == null) { revokeSession(sessionId, now); return null; }
        String next = randomToken(64);
        UUID replacementId = UUID.randomUUID();
        jdbc.update("insert into extension_refresh_tokens (id, session_id, family_id, token_hash, created_at, expires_at) values (?, ?, ?, ?, ?, ?)",
            replacementId, sessionId, row.get("family"), hash(next), timestamp(now), timestamp((Instant) row.get("expires")));
        jdbc.update("update extension_refresh_tokens set revoked_at = ?, replaced_by_token_id = ? where id = ?", timestamp(now), replacementId, tokenId);
        JwtService.IssuedAccessToken access = jwtService.issueExtension(user, sessionId);
        return new ExtensionTokenResponse(access.value(), next, access.expiresAt().getEpochSecond(), user.getId(), user.getEmail(), user.getRole().name());
    }

    @Transactional
    public void logout(String rawToken) {
        Map<String, Object> row = lockedRefresh(rawToken);
        if (row != null) revokeSession((UUID) row.get("session"), Instant.now());
    }

    @Transactional(readOnly = true)
    public boolean isSessionActive(UUID sessionId) {
        return sessionActive(sessionId, Instant.now());
    }

    private Map<String, Object> lockedRefresh(String rawToken) {
        if (rawToken == null || rawToken.length() > 200) return null;
        return jdbc.query("select t.id, t.session_id, t.family_id, t.replaced_by_token_id, t.expires_at, t.revoked_at, s.user_id from extension_refresh_tokens t join extension_sessions s on s.id=t.session_id where t.token_hash=? for update of t, s", rs -> {
            if (!rs.next()) return null;
            return Map.of("id", rs.getObject("id", UUID.class), "session", rs.getObject("session_id", UUID.class),
                "family", rs.getObject("family_id", UUID.class), "user", rs.getObject("user_id", UUID.class),
                "expires", rs.getTimestamp("expires_at").toInstant(), "revoked", rs.getTimestamp("revoked_at") == null ? "" : rs.getTimestamp("revoked_at").toInstant(),
                "replacement", rs.getObject("replaced_by_token_id") == null ? "" : rs.getObject("replaced_by_token_id"));
        }, hash(rawToken));
    }

    private boolean sessionActive(UUID id, Instant now) {
        return jdbc.queryForObject("select exists(select 1 from extension_sessions s where s.id=? and s.revoked_at is null and s.expires_at>? and exists (select 1 from refresh_tokens r where r.user_id=s.user_id and r.family_id=s.website_family_id and r.revoked_at is null and r.expires_at>?))",
            Boolean.class, id, timestamp(now), timestamp(now));
    }

    private boolean websiteFamilyActive(UUID userId, UUID familyId, Instant now) {
        return jdbc.queryForObject("select exists(select 1 from refresh_tokens where user_id=? and family_id=? and revoked_at is null and expires_at>?)",
            Boolean.class, userId, familyId, timestamp(now));
    }

    private void revokeSession(UUID id, Instant now) {
        jdbc.update("update extension_sessions set revoked_at=coalesce(revoked_at, ?) where id=?", timestamp(now), id);
        jdbc.update("update extension_refresh_tokens set revoked_at=coalesce(revoked_at, ?) where session_id=?", timestamp(now), id);
    }

    private void requireClient(String clientId, String redirectUri) {
        if (clientId == null || !safeEquals(properties.getExtensionClients().get(clientId), redirectUri)) throw invalidRequest();
    }

    private static ApiException invalidRequest() { return new ApiException(HttpStatus.BAD_REQUEST, "invalid_extension_authorization", "Invalid extension authorization request"); }
    private static ApiException unauthorized() { return new ApiException(HttpStatus.UNAUTHORIZED, "authentication_required", "Authentication required"); }
    private static String randomToken(int size) { byte[] bytes = new byte[size]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String hash(String value) { return HexFormat.of().formatHex(sha256(value)); }
    private static Timestamp timestamp(Instant value) { return Timestamp.from(value); }
    private static byte[] sha256(String value) { try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); } catch (Exception ex) { throw new IllegalStateException("SHA-256 unavailable", ex); } }
    private static boolean safeEquals(String first, String second) { return first != null && second != null && MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8)); }

    public record ExtensionTokenResponse(String accessToken, String refreshToken, long expiresAt, UUID userId, String email, String role) { }
}

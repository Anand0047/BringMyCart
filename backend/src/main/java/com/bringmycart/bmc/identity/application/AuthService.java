package com.bringmycart.bmc.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import com.bringmycart.bmc.identity.api.dto.AuthResponse;
import com.bringmycart.bmc.identity.api.dto.RegisterRequest;
import com.bringmycart.bmc.identity.domain.RefreshToken;
import com.bringmycart.bmc.identity.domain.Role;
import com.bringmycart.bmc.identity.domain.UserAccount;
import com.bringmycart.bmc.identity.exception.ApiException;
import com.bringmycart.bmc.identity.exception.RefreshTokenRejectedException;
import com.bringmycart.bmc.identity.infrastructure.RefreshTokenRepository;
import com.bringmycart.bmc.identity.infrastructure.UserAccountRepository;
import com.bringmycart.bmc.identity.security.AuthProperties;
import com.bringmycart.bmc.identity.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserAccountRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthProperties properties;
    private final AuthenticationRateLimiter rateLimiter;

    public AuthService(UserAccountRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                       JwtService jwtService, AuthProperties properties, AuthenticationRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public UUID register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        rateLimiter.check("register", email);
        if (userRepository.existsByEmail(email)) {
            rateLimiter.recordFailure("register", email);
            throw new ApiException(HttpStatus.CONFLICT, "email_in_use", "An account already exists for this email");
        }
        try {
            UserAccount user = userRepository.save(new UserAccount(email, passwordEncoder.encode(request.password()),
                Role.CUSTOMER, request.fullName().strip()));
            rateLimiter.clear("register", email);
            return user.getId();
        } catch (DataIntegrityViolationException exception) {
            rateLimiter.recordFailure("register", email);
            throw new ApiException(HttpStatus.CONFLICT, "email_in_use", "An account already exists for this email");
        }
    }

    @Transactional
    public AuthenticationResult login(String rawEmail, String password) {
        return login(rawEmail, password, null);
    }

    @Transactional
    public AuthenticationResult login(String rawEmail, String password, String previousRefreshToken) {
        String email = normalizeEmail(rawEmail);
        rateLimiter.check("login", email);
        try {
            authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
            UserAccount user = userRepository.findByEmail(email).orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
            if (!user.isEnabled()) {
                throw new BadCredentialsException("Invalid credentials");
            }
            revokePreviousWebsiteFamily(previousRefreshToken);
            rateLimiter.clear("login", email);
            return issueTokens(user, UUID.randomUUID());
        } catch (AuthenticationException exception) {
            rateLimiter.recordFailure("login", email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid email or password");
        }
    }

    private void revokePreviousWebsiteFamily(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
            .ifPresent(token -> refreshTokenRepository.revokeActiveFamily(token.getFamilyId(), Instant.now()));
    }

    @Transactional(noRollbackFor = RefreshTokenRejectedException.class)
    public AuthenticationResult refresh(String rawToken) {
        String tokenHash = hash(rawToken);
        Instant now = Instant.now();
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
            .orElseThrow(RefreshTokenRejectedException::new);
        if (current.isRevoked()) {
            if (current.getReplacedByTokenId() != null) {
                refreshTokenRepository.revokeActiveFamily(current.getFamilyId(), now);
            }
            throw new RefreshTokenRejectedException();
        }
        if (current.isExpired(now) || !current.getUser().isEnabled()) {
            current.revoke(now);
            throw new RefreshTokenRejectedException();
        }
        AuthenticationResult replacement = issueTokens(current.getUser(), current.getFamilyId());
        current.markRotated(replacement.refreshTokenId(), now);
        return replacement;
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
            .ifPresent(token -> refreshTokenRepository.revokeActiveFamily(token.getFamilyId(), Instant.now()));
    }

    private AuthenticationResult issueTokens(UserAccount user, UUID familyId) {
        String rawRefreshToken = randomToken();
        Instant refreshExpiresAt = Instant.now().plus(properties.getRefreshTokenDays(), ChronoUnit.DAYS);
        RefreshToken persisted = refreshTokenRepository.save(new RefreshToken(user, hash(rawRefreshToken), familyId, refreshExpiresAt));
        JwtService.IssuedAccessToken accessToken = jwtService.issue(user, familyId);
        AuthResponse response = new AuthResponse(accessToken.value(), "Bearer", accessToken.expiresAt().getEpochSecond(), user.getId(), user.getEmail(), user.getRole().name());
        return new AuthenticationResult(response, rawRefreshToken, refreshExpiresAt, persisted.getId());
    }

    public String normalizeEmail(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String randomToken() {
        byte[] value = new byte[64];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record AuthenticationResult(AuthResponse response, String rawRefreshToken, Instant refreshExpiresAt, UUID refreshTokenId) { }
}

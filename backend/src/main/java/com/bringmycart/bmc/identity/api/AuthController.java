package com.bringmycart.bmc.identity.api;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.bringmycart.bmc.identity.api.dto.AuthResponse;
import com.bringmycart.bmc.identity.api.dto.LoginRequest;
import com.bringmycart.bmc.identity.api.dto.RegisterRequest;
import com.bringmycart.bmc.identity.application.AuthService;
import com.bringmycart.bmc.identity.exception.ApiException;
import com.bringmycart.bmc.identity.security.AuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "bmc_refresh";
    private final AuthService authService;
    private final AuthProperties properties;

    public AuthController(AuthService authService, AuthProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, UUID>> register(@Valid @RequestBody RegisterRequest request) {
        UUID userId = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("userId", userId));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              @CookieValue(name = REFRESH_COOKIE, required = false) String previousRefreshToken,
                                              HttpServletResponse servletResponse) {
        AuthService.AuthenticationResult result = authService.login(request.email(), request.password(), previousRefreshToken);
        addRefreshCookie(servletResponse, result);
        return ResponseEntity.ok(result.response());
    }

    /**
     * Establishes the readable double-submit CSRF cookie before a browser sends
     * its first cookie-authenticated POST. The Spring Security filter creates
     * the token; the value is intentionally not returned in this API body.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Void> initializeCsrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                                 HttpServletResponse servletResponse) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh_token", "Invalid refresh token");
        }
        AuthService.AuthenticationResult result = authService.refresh(refreshToken);
        addRefreshCookie(servletResponse, result);
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                       HttpServletResponse servletResponse) {
        authService.logout(refreshToken);
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true).secure(properties.isRefreshCookieSecure()).sameSite("Strict")
            .path("/api/v1/auth").maxAge(Duration.ZERO).build().toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return Map.of("userId", jwt.getSubject(), "role", jwt.getClaimAsString("role"));
    }

    private void addRefreshCookie(HttpServletResponse response, AuthService.AuthenticationResult result) {
        Duration maxAge = Duration.between(java.time.Instant.now(), result.refreshExpiresAt());
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, result.rawRefreshToken())
            .httpOnly(true)
            .secure(properties.isRefreshCookieSecure())
            .sameSite("Strict")
            .path("/api/v1/auth")
            .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

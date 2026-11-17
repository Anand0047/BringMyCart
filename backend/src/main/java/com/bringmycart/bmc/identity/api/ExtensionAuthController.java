package com.bringmycart.bmc.identity.api;

import java.util.UUID;

import com.bringmycart.bmc.identity.application.ExtensionAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.bringmycart.bmc.identity.exception.ApiException;

@RestController
@RequestMapping("/api/v1/extension-auth")
public class ExtensionAuthController {
    private final ExtensionAuthService service;

    public ExtensionAuthController(ExtensionAuthService service) { this.service = service; }

    @PostMapping("/authorize")
    public AuthorizationResponse authorize(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AuthorizationRequest request) {
        String familyClaim = jwt.getClaimAsString("web_family");
        if (jwt.hasClaim("sid") || familyClaim == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "website_session_required", "Authorize this extension from the BringMyCart website");
        }
        UUID userId = userId(jwt);
        UUID websiteFamilyId;
        try { websiteFamilyId = UUID.fromString(familyClaim); }
        catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.FORBIDDEN, "website_session_required", "Authorize this extension from the BringMyCart website");
        }
        return new AuthorizationResponse(service.authorize(userId, websiteFamilyId, request.clientId(), request.redirectUri(), request.state(), request.codeChallenge()));
    }

    @PostMapping("/token")
    public ResponseEntity<ExtensionAuthService.ExtensionTokenResponse> exchange(@Valid @RequestBody TokenRequest request) {
        ExtensionAuthService.ExtensionTokenResponse result = service.exchange(request.code(), request.codeVerifier(), request.state(), request.clientId(), request.redirectUri());
        if (result == null) throw invalidGrant();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ExtensionAuthService.ExtensionTokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        ExtensionAuthService.ExtensionTokenResponse result = service.rotate(request.refreshToken());
        if (result == null) throw invalidGrant();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        service.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(Jwt jwt) {
        try { return UUID.fromString(jwt.getSubject()); }
        catch (IllegalArgumentException exception) { throw new ApiException(HttpStatus.UNAUTHORIZED, "authentication_required", "Authentication required"); }
    }
    private static ApiException invalidGrant() { return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_extension_grant", "The extension session is no longer valid"); }

    public record AuthorizationRequest(@NotBlank @Size(max = 100) String clientId,
                                       @NotBlank @Size(max = 500) String redirectUri,
                                       @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{32,128}") String state,
                                       @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String codeChallenge) { }
    public record AuthorizationResponse(String redirectUrl) { }
    public record TokenRequest(@NotBlank @Size(max = 200) String code,
                              @NotBlank @Pattern(regexp = "[A-Za-z0-9._~-]{43,128}") String codeVerifier,
                              @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{32,128}") String state,
                              @NotBlank @Size(max = 100) String clientId,
                              @NotBlank @Size(max = 500) String redirectUri) { }
    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) { }
}

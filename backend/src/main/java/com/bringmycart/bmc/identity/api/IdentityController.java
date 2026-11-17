package com.bringmycart.bmc.identity.api;

import java.util.UUID;
import java.util.Map;

import com.bringmycart.bmc.identity.api.dto.UserProfileResponse;
import com.bringmycart.bmc.identity.application.IdentityService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity")
public class IdentityController {
    private final IdentityService identityService;

    public IdentityController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping("/me")
    public UserProfileResponse me(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return identityService.profile(userId);
    }

    @GetMapping("/admin/ping")
    public Map<String, String> adminPing() {
        return Map.of("status", "admin");
    }
}

package com.bringmycart.bmc.identity.api;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity")
public class IdentityController {
    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return Map.of("userId", jwt.getSubject(), "role", jwt.getClaimAsString("role"));
    }

    @GetMapping("/admin/ping")
    public Map<String, String> adminPing() {
        return Map.of("status", "admin");
    }
}

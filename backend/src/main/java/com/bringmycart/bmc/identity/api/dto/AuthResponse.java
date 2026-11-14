package com.bringmycart.bmc.identity.api.dto;

import java.util.UUID;

public record AuthResponse(String accessToken, String tokenType, long expiresAt, UUID userId, String email, String role) { }

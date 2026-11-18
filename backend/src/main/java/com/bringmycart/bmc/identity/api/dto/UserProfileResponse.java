package com.bringmycart.bmc.identity.api.dto;

import java.util.UUID;

public record UserProfileResponse(UUID userId, String email, String fullName, String role) { }

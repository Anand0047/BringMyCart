package com.bringmycart.bmc.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must be 100 characters or fewer")
    @Pattern(regexp = "(?U)\\s*[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*\\s*", message = "Full name contains unsupported characters")
    String fullName,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 12, max = 128)
    @Pattern(regexp = "(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,128}", message = "Password must include upper, lower, digit, and symbol")
    String password
) { }

package com.bringmycart.bmc.savedproducts.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExtensionProductMetadata(
    @Size(max = 500) String title,
    @Size(max = 255) String brand,
    @Size(max = 2048) @Pattern(regexp = "^https://[^\\s]+$", message = "imageUrl must use HTTPS") String imageUrl,
    @DecimalMin(value = "0.01", message = "price must be positive") BigDecimal price,
    @Pattern(regexp = "^(INR)?$", message = "currency must be INR") String currency,
    @Pattern(regexp = "^(IN_STOCK|OUT_OF_STOCK|UNKNOWN)?$") String availability
) { }

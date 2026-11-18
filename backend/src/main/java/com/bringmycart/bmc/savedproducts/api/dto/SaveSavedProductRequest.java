package com.bringmycart.bmc.savedproducts.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

public record SaveSavedProductRequest(
    @NotBlank(message = "url is required")
    @Size(max = 2_048, message = "url must not exceed 2048 characters")
    String url,
    @Valid ExtensionProductMetadata metadata
) {
    public SaveSavedProductRequest(String url) {
        this(url, null);
    }
}

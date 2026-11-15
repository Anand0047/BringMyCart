package com.bringmycart.bmc.savedproducts.api.dto;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

import com.bringmycart.bmc.savedproducts.domain.SavedProduct;

public record SavedProductResponse(
    UUID id,
    String asin,
    String originalUrl,
    String canonicalUrl,
    String parentAsin,
    String title,
    String brand,
    String imageUrl,
    BigDecimal initialPrice,
    BigDecimal finalPrice,
    String currency,
    BigDecimal discountPercentage,
    BigDecimal rating,
    Long reviewsCount,
    String availability,
    String enrichmentStatus,
    String enrichmentErrorCategory,
    Instant metadataFetchedAt,
    Instant providerFetchedAt,
    Instant createdAt,
    Instant updatedAt
) {
    public static SavedProductResponse from(SavedProduct product) {
        return new SavedProductResponse(
            product.getId(),
            product.getAsin(),
            product.getOriginalUrl(),
            product.getCanonicalUrl(),
            product.getParentAsin(),
            product.getTitle(),
            product.getBrand(),
            product.getImageUrl(),
            product.getInitialPrice(),
            product.getFinalPrice(),
            product.getCurrency(),
            product.getDiscountPercentage(),
            product.getRating(),
            product.getReviewsCount(),
            product.getAvailability(),
            product.getEnrichmentStatus().name(),
            product.getEnrichmentErrorCategory(),
            product.getMetadataFetchedAt(),
            product.getProviderFetchedAt(),
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }
}

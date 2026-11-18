package com.bringmycart.bmc.savedproducts.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Provider-neutral, observed metadata. It intentionally excludes customer-review content until
 * the selected metadata provider's applicable storage and usage terms have been verified.
 */
public record ProductMetadata(
    String asin,
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
    Instant providerFetchedAt,
    String description,
    List<String> imageUrls,
    List<String> features,
    List<String> categories,
    JsonNode specifications,
    String manufacturer,
    String source
) {
    public ProductMetadata(String asin, String parentAsin, String title, String brand, String imageUrl,
                           BigDecimal initialPrice, BigDecimal finalPrice, String currency,
                           BigDecimal discountPercentage, BigDecimal rating, Long reviewsCount,
                           String availability, Instant providerFetchedAt) {
        this(asin, parentAsin, title, brand, imageUrl, initialPrice, finalPrice, currency,
            discountPercentage, rating, reviewsCount, availability, providerFetchedAt,
            null, List.of(), List.of(), List.of(), null, null, null);
    }
}

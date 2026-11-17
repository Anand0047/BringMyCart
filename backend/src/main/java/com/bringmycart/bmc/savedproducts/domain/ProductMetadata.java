package com.bringmycart.bmc.savedproducts.domain;

import java.math.BigDecimal;
import java.time.Instant;

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
    Instant providerFetchedAt
) {
}

package com.bringmycart.bmc.savedproducts.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.bringmycart.bmc.retailer.Retailer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SavedProductFallbackMetadataTest {
    @Test
    void acceptsSufficientMetadataCapturedFromExtension() {
        SavedProduct product = new SavedProduct(null, Retailer.FLIPKART, "MOBABCDE1234", null,
            "https://www.flipkart.com/p/item?pid=MOBABCDE1234", "https://www.flipkart.com/p/item?pid=MOBABCDE1234");
        product.applyClientMetadata("Product title", null, "https://images.example/item.jpg", null, null, "UNKNOWN");

        assertThat(product.hasFallbackMetadata()).isTrue();
    }

    @Test
    void doesNotTreatPreviouslyEnrichedProviderMetadataAsExtensionFallback() {
        SavedProduct product = new SavedProduct(null, Retailer.AMAZON, "B0D2MW6L54", "B0D2MW6L54",
            "https://www.amazon.in/dp/B0D2MW6L54", "https://www.amazon.in/dp/B0D2MW6L54");
        product.completeEnrichment(new ProductMetadata("B0D2MW6L54", null, "Existing title", null,
            "https://images.example/item.jpg", null, new BigDecimal("7999"), "INR", null, null,
            null, "IN_STOCK", Instant.parse("2026-11-15T10:00:00Z"), null, List.of(), List.of(),
            List.of(), null, null, "AXESSO"), Instant.parse("2026-11-15T10:00:00Z"));

        assertThat(product.hasFallbackMetadata()).isFalse();
    }
}

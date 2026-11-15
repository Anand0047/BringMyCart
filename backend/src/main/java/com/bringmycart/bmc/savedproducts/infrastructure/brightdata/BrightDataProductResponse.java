package com.bringmycart.bmc.savedproducts.infrastructure.brightdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BrightDataProductResponse(
    String asin,
    @JsonProperty("parent_asin") String parentAsin,
    String title,
    String brand,
    @JsonProperty("image_url") String imageUrl,
    @JsonProperty("initial_price") JsonNode initialPrice,
    @JsonProperty("final_price") JsonNode finalPrice,
    String currency,
    JsonNode rating,
    @JsonProperty("reviews_count") JsonNode reviewsCount,
    String availability,
    String timestamp
) {
}

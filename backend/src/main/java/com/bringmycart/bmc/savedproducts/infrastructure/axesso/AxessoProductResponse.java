package com.bringmycart.bmc.savedproducts.infrastructure.axesso;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AxessoProductResponse(
    String asin,
    @JsonAlias({"parent_asin", "parentASIN"}) String parentAsin,
    String productTitle,
    JsonNode price,
    JsonNode retailPrice,
    JsonNode mainImage,
    JsonNode imageUrlList,
    String productDescription,
    JsonNode warehouseAvailability,
    JsonNode productRating,
    JsonNode countReview,
    JsonNode features,
    JsonNode categories,
    JsonNode productDetails,
    JsonNode manufacturer,
    String currency,
    String timestamp
) { }

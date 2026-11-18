package com.bringmycart.bmc.retailer;

public record ValidatedRetailerProductUrl(
    Retailer retailer,
    String productId,
    String originalUrl,
    String canonicalUrl,
    String asin
) { }

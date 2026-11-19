package com.bringmycart.bmc.retailer;

public record ValidatedRetailerProductUrl(
    Retailer retailer,
    String productId,
    String originalUrl,
    String canonicalUrl,
    String asin,
    String variantId,
    String variantLabel
) {
    public ValidatedRetailerProductUrl(Retailer retailer, String productId, String originalUrl,
                                       String canonicalUrl, String asin) {
        this(retailer, productId, originalUrl, canonicalUrl, asin, null, null);
    }
}

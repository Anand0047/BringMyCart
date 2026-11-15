package com.bringmycart.bmc.savedproducts.validation;

/**
 * A validated Amazon India product URL. The submitted URL is retained for user-facing history;
 * the canonical URL is the stable URL used by future integrations.
 */
public record ValidatedAmazonProductUrl(String originalUrl, String canonicalUrl, String asin) {
}

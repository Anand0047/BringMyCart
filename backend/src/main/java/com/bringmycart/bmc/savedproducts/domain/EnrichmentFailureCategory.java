package com.bringmycart.bmc.savedproducts.domain;

/** Safe, non-sensitive categories that may be retained and shown to operations. */
public enum EnrichmentFailureCategory {
    NOT_CONFIGURED,
    AUTHENTICATION,
    RATE_LIMITED,
    PROVIDER_UNAVAILABLE,
    TIMEOUT,
    INVALID_RESPONSE,
    EXECUTOR_SATURATED,
    UNEXPECTED
}

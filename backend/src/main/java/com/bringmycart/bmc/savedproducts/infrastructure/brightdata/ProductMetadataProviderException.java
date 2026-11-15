package com.bringmycart.bmc.savedproducts.infrastructure.brightdata;

public class ProductMetadataProviderException extends RuntimeException {
    private final FailureType failureType;

    public ProductMetadataProviderException(FailureType failureType, String message) {
        super(message);
        this.failureType = failureType;
    }

    public ProductMetadataProviderException(FailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
    }

    public FailureType getFailureType() { return failureType; }

    public enum FailureType {
        NOT_CONFIGURED,
        AUTHENTICATION,
        RATE_LIMITED,
        REMOTE_FAILURE,
        TIMEOUT,
        INVALID_RESPONSE
    }
}

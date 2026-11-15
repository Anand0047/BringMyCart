package com.bringmycart.bmc.savedproducts.application;

import java.util.Optional;
import java.util.UUID;

import com.bringmycart.bmc.savedproducts.domain.EnrichmentFailureCategory;
import com.bringmycart.bmc.savedproducts.domain.ProductMetadata;
import com.bringmycart.bmc.savedproducts.infrastructure.brightdata.ProductMetadataProviderException;
import org.springframework.stereotype.Service;

@Service
public class ProductEnrichmentProcessor {
    private final ProductEnrichmentStateService stateService;
    private final ProductMetadataProvider metadataProvider;

    public ProductEnrichmentProcessor(ProductEnrichmentStateService stateService,
                                      ProductMetadataProvider metadataProvider) {
        this.stateService = stateService;
        this.metadataProvider = metadataProvider;
    }

    /**
     * This method deliberately has no surrounding transaction. Claim, provider I/O, and completion
     * are separate operations so a slow external call cannot hold a database transaction open.
     */
    public void process(UUID savedProductId) {
        Optional<ProductEnrichmentStateService.EnrichmentJob> job = stateService.claim(savedProductId);
        if (job.isEmpty()) {
            return;
        }

        try {
            ProductMetadata metadata = metadataProvider.fetch(job.get().asin());
            stateService.complete(savedProductId, metadata);
        } catch (ProductMetadataProviderException exception) {
            stateService.fail(savedProductId, categoryFor(exception), isRetryable(exception));
        } catch (RuntimeException exception) {
            // Retain only a safe category; raw provider/client messages can include sensitive details.
            stateService.fail(savedProductId, EnrichmentFailureCategory.UNEXPECTED, false);
        }
    }

    private EnrichmentFailureCategory categoryFor(ProductMetadataProviderException exception) {
        return switch (exception.getFailureType()) {
            case NOT_CONFIGURED -> EnrichmentFailureCategory.NOT_CONFIGURED;
            case AUTHENTICATION -> EnrichmentFailureCategory.AUTHENTICATION;
            case RATE_LIMITED -> EnrichmentFailureCategory.RATE_LIMITED;
            case REMOTE_FAILURE -> EnrichmentFailureCategory.PROVIDER_UNAVAILABLE;
            case TIMEOUT -> EnrichmentFailureCategory.TIMEOUT;
            case INVALID_RESPONSE -> EnrichmentFailureCategory.INVALID_RESPONSE;
        };
    }

    private boolean isRetryable(ProductMetadataProviderException exception) {
        return switch (exception.getFailureType()) {
            case RATE_LIMITED, REMOTE_FAILURE, TIMEOUT -> true;
            case NOT_CONFIGURED, AUTHENTICATION, INVALID_RESPONSE -> false;
        };
    }
}

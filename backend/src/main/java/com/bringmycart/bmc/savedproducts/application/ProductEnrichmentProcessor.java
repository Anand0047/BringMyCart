package com.bringmycart.bmc.savedproducts.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import com.bringmycart.bmc.retailer.Retailer;
import com.bringmycart.bmc.retailer.scrapedo.ScrapeDoProductMetadataProvider;

import com.bringmycart.bmc.savedproducts.domain.EnrichmentFailureCategory;
import com.bringmycart.bmc.savedproducts.domain.ProductMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProductEnrichmentProcessor {
    private static final Logger log = LoggerFactory.getLogger(ProductEnrichmentProcessor.class);
    private final ProductEnrichmentStateService stateService;
    private final ProductMetadataProvider metadataProvider;
    private final ScrapeDoProductMetadataProvider scrapeDoProvider;

    @Autowired
    public ProductEnrichmentProcessor(ProductEnrichmentStateService stateService,
                                      ProductMetadataProvider metadataProvider,
                                      ScrapeDoProductMetadataProvider scrapeDoProvider) {
        this.stateService = stateService;
        this.metadataProvider = metadataProvider;
        this.scrapeDoProvider = scrapeDoProvider;
    }

    public ProductEnrichmentProcessor(ProductEnrichmentStateService stateService, ProductMetadataProvider metadataProvider) {
        this(stateService, metadataProvider, null);
    }

    /**
     * This method deliberately has no surrounding transaction. Claim, provider I/O, and completion
     * are separate operations so a slow external call cannot hold a database transaction open.
     */
    public void process(UUID savedProductId) {
        Optional<ProductEnrichmentStateService.EnrichmentJob> job = stateService.claim(savedProductId);
        if (job.isEmpty()) {
            log.info("Saved-product enrichment skipped reason=job_not_claimable_or_not_found");
            return;
        }
        log.info("Saved-product enrichment claimed status=PROCESSING");

        try {
            log.info("Saved-product metadata provider invocation started");
            ProductMetadata metadata;
            if (job.get().retailer() == Retailer.UNSUPPORTED) {
                throw new ProductMetadataProviderException(ProductMetadataProviderException.FailureType.INVALID_RESPONSE,
                    "Saved product belongs to a retired retailer");
            }
            if (job.get().retailer() == Retailer.AMAZON) {
                metadata = metadataProvider.fetch(job.get().asin());
            } else if (scrapeDoProvider == null) {
                metadata = unavailableProvider();
            } else {
                metadata = scrapeDoProvider.fetch(job.get().retailer(), job.get().canonicalUrl());
            }
            if (!hasUsefulMetadata(metadata)) {
                throw new ProductMetadataProviderException(ProductMetadataProviderException.FailureType.INVALID_RESPONSE,
                    "Provider returned incomplete product metadata");
            }
            log.info("Saved-product metadata provider returned successfully");
            boolean persisted = stateService.complete(savedProductId, metadata);
            log.info("Saved-product enrichment finalStatus={} reason={}", persisted ? "COMPLETED" : "UNCHANGED",
                persisted ? "metadata_persisted" : "product_missing_or_not_processing");
        } catch (ProductMetadataProviderException exception) {
            EnrichmentFailureCategory category = categoryFor(exception);
            boolean fallbackCompleted = stateService.completeUsingExistingFallback(savedProductId);
            boolean persisted = fallbackCompleted || stateService.fail(savedProductId, category, isRetryable(exception));
            log.warn("Saved-product enrichment finalStatus={} reason={} providerFailureType={}",
                fallbackCompleted ? "COMPLETED" : persisted ? "FAILED" : "UNCHANGED",
                fallbackCompleted ? "extension_metadata_fallback" : persisted ? category : "product_missing_or_not_processing",
                exception.getFailureType());
        } catch (RuntimeException exception) {
            // Retain only a safe category; raw provider/client messages can include sensitive details.
            boolean fallbackCompleted = stateService.completeUsingExistingFallback(savedProductId);
            boolean persisted = fallbackCompleted || stateService.fail(savedProductId, EnrichmentFailureCategory.UNEXPECTED, false);
            log.error("Saved-product enrichment finalStatus={} reason={} exceptionType={}",
                fallbackCompleted ? "COMPLETED" : persisted ? "FAILED" : "UNCHANGED",
                fallbackCompleted ? "extension_metadata_fallback" : persisted ? "UNEXPECTED" : "product_missing_or_not_processing",
                exception.getClass().getSimpleName());
        }
    }

    private ProductMetadata unavailableProvider() {
        throw new ProductMetadataProviderException(ProductMetadataProviderException.FailureType.NOT_CONFIGURED,
            "Retailer metadata provider is unavailable");
    }

    private boolean hasUsefulMetadata(ProductMetadata metadata) {
        return metadata != null && ((metadata.title() != null && !metadata.title().isBlank())
            || metadata.imageUrl() != null || metadata.finalPrice() != null);
    }

    private EnrichmentFailureCategory categoryFor(ProductMetadataProviderException exception) {
        return switch (exception.getFailureType()) {
            case NOT_CONFIGURED -> EnrichmentFailureCategory.NOT_CONFIGURED;
            case AUTHENTICATION -> EnrichmentFailureCategory.AUTHENTICATION;
            case RATE_LIMITED, QUOTA_EXHAUSTED -> EnrichmentFailureCategory.RATE_LIMITED;
            case REMOTE_FAILURE -> EnrichmentFailureCategory.PROVIDER_UNAVAILABLE;
            case TIMEOUT -> EnrichmentFailureCategory.TIMEOUT;
            case INVALID_RESPONSE -> EnrichmentFailureCategory.INVALID_RESPONSE;
        };
    }

    private boolean isRetryable(ProductMetadataProviderException exception) {
        return switch (exception.getFailureType()) {
            case RATE_LIMITED, REMOTE_FAILURE, TIMEOUT -> true;
            case NOT_CONFIGURED, AUTHENTICATION, QUOTA_EXHAUSTED, INVALID_RESPONSE -> false;
        };
    }
}

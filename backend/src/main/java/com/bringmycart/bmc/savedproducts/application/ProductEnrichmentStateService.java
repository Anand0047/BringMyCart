package com.bringmycart.bmc.savedproducts.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.bringmycart.bmc.savedproducts.domain.EnrichmentFailureCategory;
import com.bringmycart.bmc.savedproducts.domain.EnrichmentStatus;
import com.bringmycart.bmc.savedproducts.domain.ProductMetadata;
import com.bringmycart.bmc.savedproducts.domain.SavedProduct;
import com.bringmycart.bmc.savedproducts.infrastructure.SavedProductRepository;
import com.bringmycart.bmc.retailer.Retailer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductEnrichmentStateService {
    private final SavedProductRepository savedProductRepository;
    private final ProductEnrichmentProperties properties;

    public ProductEnrichmentStateService(SavedProductRepository savedProductRepository,
                                         ProductEnrichmentProperties properties) {
        this.savedProductRepository = savedProductRepository;
        this.properties = properties;
    }

    @Transactional
    public Optional<EnrichmentJob> claim(UUID savedProductId) {
        Instant now = Instant.now();
        int claimed = savedProductRepository.claimForEnrichment(savedProductId, now,
            now.minus(properties.getStaleAfter()), properties.getMaxAttempts());
        if (claimed == 0) {
            return Optional.empty();
        }
        return savedProductRepository.findById(savedProductId)
            .map(product -> new EnrichmentJob(product.getId(), product.getRetailer(), product.getRetailerProductId(),
                product.getAsin(), product.getCanonicalUrl()));
    }

    @Transactional
    public boolean complete(UUID savedProductId, ProductMetadata metadata) {
        Optional<SavedProduct> product = savedProductRepository.findById(savedProductId);
        if (product.isEmpty() || product.get().getEnrichmentStatus() != EnrichmentStatus.PROCESSING) {
            return false;
        }
        product.get().completeEnrichment(metadata, Instant.now());
        return true;
    }

    @Transactional
    public boolean completeUsingExistingFallback(UUID savedProductId) {
        Optional<SavedProduct> product = savedProductRepository.findById(savedProductId);
        if (product.isEmpty() || product.get().getEnrichmentStatus() != EnrichmentStatus.PROCESSING
            || !product.get().hasFallbackMetadata()) return false;
        product.get().completeUsingExistingMetadata(Instant.now());
        return true;
    }

    @Transactional
    public boolean fail(UUID savedProductId, EnrichmentFailureCategory category, boolean retryable) {
        Optional<SavedProduct> product = savedProductRepository.findById(savedProductId);
        if (product.isEmpty() || product.get().getEnrichmentStatus() != EnrichmentStatus.PROCESSING) {
            return false;
        }
        Instant nextAttemptAt = retryable && properties.isRecoveryEnabled()
            && product.get().getEnrichmentAttempts() < properties.getMaxAttempts()
            ? Instant.now().plus(properties.getRetryDelay()) : null;
        product.get().failEnrichment(category, nextAttemptAt);
        return true;
    }

    @Transactional
    public boolean markExecutorSaturated(UUID savedProductId) {
        Optional<SavedProduct> product = savedProductRepository.findById(savedProductId);
        if (product.isEmpty() || product.get().getEnrichmentStatus() != EnrichmentStatus.PENDING) {
            return false;
        }
        product.get().failEnrichment(EnrichmentFailureCategory.EXECUTOR_SATURATED,
            properties.isRecoveryEnabled() ? Instant.now().plus(properties.getRetryDelay()) : null);
        return true;
    }

    public record EnrichmentJob(UUID savedProductId, Retailer retailer, String retailerProductId,
                                String asin, String canonicalUrl) {
    }
}

package com.bringmycart.bmc.savedproducts.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.bringmycart.bmc.savedproducts.domain.EnrichmentFailureCategory;
import com.bringmycart.bmc.savedproducts.domain.EnrichmentStatus;
import com.bringmycart.bmc.savedproducts.domain.ProductMetadata;
import com.bringmycart.bmc.savedproducts.domain.SavedProduct;
import com.bringmycart.bmc.savedproducts.infrastructure.SavedProductRepository;
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
            .map(product -> new EnrichmentJob(product.getId(), product.getAsin()));
    }

    @Transactional
    public void complete(UUID savedProductId, ProductMetadata metadata) {
        savedProductRepository.findById(savedProductId).ifPresent(product -> {
            if (product.getEnrichmentStatus() == EnrichmentStatus.PROCESSING) {
                product.completeEnrichment(metadata, Instant.now());
            }
        });
    }

    @Transactional
    public void fail(UUID savedProductId, EnrichmentFailureCategory category, boolean retryable) {
        savedProductRepository.findById(savedProductId).ifPresent(product -> {
            if (product.getEnrichmentStatus() == EnrichmentStatus.PROCESSING) {
                Instant nextAttemptAt = retryable && properties.isRecoveryEnabled()
                    && product.getEnrichmentAttempts() < properties.getMaxAttempts()
                    ? Instant.now().plus(properties.getRetryDelay()) : null;
                product.failEnrichment(category, nextAttemptAt);
            }
        });
    }

    @Transactional
    public void markExecutorSaturated(UUID savedProductId) {
        savedProductRepository.findById(savedProductId).ifPresent(product -> {
            if (product.getEnrichmentStatus() == EnrichmentStatus.PENDING) {
                product.failEnrichment(EnrichmentFailureCategory.EXECUTOR_SATURATED,
                    properties.isRecoveryEnabled() ? Instant.now().plus(properties.getRetryDelay()) : null);
            }
        });
    }

    public record EnrichmentJob(UUID savedProductId, String asin) {
    }
}

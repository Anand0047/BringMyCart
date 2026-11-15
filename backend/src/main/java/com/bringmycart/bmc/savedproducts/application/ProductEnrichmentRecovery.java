package com.bringmycart.bmc.savedproducts.application;

import java.time.Instant;

import com.bringmycart.bmc.savedproducts.infrastructure.SavedProductRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Conservative crash/rejection recovery only. It never refreshes COMPLETED metadata and is absent
 * unless explicitly enabled through PRODUCT_ENRICHMENT_RECOVERY_ENABLED.
 */
@Component
@ConditionalOnProperty(prefix = "bmc.product-enrichment", name = "recovery-enabled", havingValue = "true")
public class ProductEnrichmentRecovery {
    private final SavedProductRepository savedProductRepository;
    private final ProductEnrichmentDispatcher dispatcher;
    private final ProductEnrichmentProperties properties;

    public ProductEnrichmentRecovery(SavedProductRepository savedProductRepository,
                                     ProductEnrichmentDispatcher dispatcher,
                                     ProductEnrichmentProperties properties) {
        this.savedProductRepository = savedProductRepository;
        this.dispatcher = dispatcher;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${bmc.product-enrichment.recovery-interval:PT15M}")
    public void recover() {
        Instant now = Instant.now();
        savedProductRepository.findRecoverableIds(now, now.minus(properties.getStaleAfter()), properties.getMaxAttempts(),
                PageRequest.of(0, properties.getRecoveryBatchSize()))
            .forEach(dispatcher::dispatch);
    }
}

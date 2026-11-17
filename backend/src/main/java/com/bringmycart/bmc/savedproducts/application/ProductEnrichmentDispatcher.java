package com.bringmycart.bmc.savedproducts.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ProductEnrichmentDispatcher {
    private static final Logger log = LoggerFactory.getLogger(ProductEnrichmentDispatcher.class);
    private final ProductEnrichmentProcessor processor;

    public ProductEnrichmentDispatcher(ProductEnrichmentProcessor processor) {
        this.processor = processor;
    }

    @Async("productEnrichmentExecutor")
    public void dispatch(UUID savedProductId) {
        log.info("Saved-product enrichment worker started");
        processor.process(savedProductId);
    }
}

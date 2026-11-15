package com.bringmycart.bmc.savedproducts.application;

import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ProductEnrichmentDispatcher {
    private final ProductEnrichmentProcessor processor;

    public ProductEnrichmentDispatcher(ProductEnrichmentProcessor processor) {
        this.processor = processor;
    }

    @Async("productEnrichmentExecutor")
    public void dispatch(UUID savedProductId) {
        processor.process(savedProductId);
    }
}

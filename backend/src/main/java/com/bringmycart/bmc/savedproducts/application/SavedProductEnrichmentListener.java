package com.bringmycart.bmc.savedproducts.application;

import org.springframework.core.task.TaskRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SavedProductEnrichmentListener {
    private static final Logger log = LoggerFactory.getLogger(SavedProductEnrichmentListener.class);
    private final ProductEnrichmentDispatcher dispatcher;
    private final ProductEnrichmentStateService stateService;

    public SavedProductEnrichmentListener(ProductEnrichmentDispatcher dispatcher,
                                          ProductEnrichmentStateService stateService) {
        this.dispatcher = dispatcher;
        this.stateService = stateService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterSavedProductCommit(SavedProductCreatedEvent event) {
        log.info("Saved-product enrichment triggered after save commit");
        dispatch(event.savedProductId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterManualRefreshCommit(SavedProductRefreshRequestedEvent event) {
        log.info("Saved-product enrichment triggered after manual refresh commit");
        dispatch(event.savedProductId());
    }

    private void dispatch(java.util.UUID savedProductId) {
        try {
            dispatcher.dispatch(savedProductId);
            log.info("Saved-product enrichment submitted to background executor");
        } catch (TaskRejectedException exception) {
            log.warn("Saved-product enrichment dispatch rejected; executor capacity reached");
            boolean markedFailed = stateService.markExecutorSaturated(savedProductId);
            log.info("Saved-product enrichment finalStatus={} reason=EXECUTOR_SATURATED persisted={}",
                markedFailed ? "FAILED" : "UNCHANGED", markedFailed);
        }
    }
}

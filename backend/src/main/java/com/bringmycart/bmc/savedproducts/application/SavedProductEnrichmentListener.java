package com.bringmycart.bmc.savedproducts.application;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SavedProductEnrichmentListener {
    private final ProductEnrichmentDispatcher dispatcher;
    private final ProductEnrichmentStateService stateService;

    public SavedProductEnrichmentListener(ProductEnrichmentDispatcher dispatcher,
                                          ProductEnrichmentStateService stateService) {
        this.dispatcher = dispatcher;
        this.stateService = stateService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterSavedProductCommit(SavedProductCreatedEvent event) {
        dispatch(event.savedProductId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterManualRefreshCommit(SavedProductRefreshRequestedEvent event) {
        dispatch(event.savedProductId());
    }

    private void dispatch(java.util.UUID savedProductId) {
        try {
            dispatcher.dispatch(savedProductId);
        } catch (TaskRejectedException exception) {
            stateService.markExecutorSaturated(savedProductId);
        }
    }
}

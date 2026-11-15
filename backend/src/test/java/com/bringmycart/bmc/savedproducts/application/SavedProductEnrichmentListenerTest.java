package com.bringmycart.bmc.savedproducts.application;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SavedProductEnrichmentListenerTest {
    @Test
    void executorSaturationLeavesARecoverableSafeFailureState() {
        ProductEnrichmentDispatcher dispatcher = mock(ProductEnrichmentDispatcher.class);
        ProductEnrichmentStateService stateService = mock(ProductEnrichmentStateService.class);
        UUID productId = UUID.randomUUID();
        doThrow(new TaskRejectedException("executor saturated")).when(dispatcher).dispatch(productId);

        new SavedProductEnrichmentListener(dispatcher, stateService)
            .afterSavedProductCommit(new SavedProductCreatedEvent(productId));

        verify(stateService).markExecutorSaturated(productId);
    }
}

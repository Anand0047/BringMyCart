package com.bringmycart.bmc.savedproducts.application;

import java.util.List;
import java.util.UUID;

import com.bringmycart.bmc.savedproducts.infrastructure.SavedProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductEnrichmentRecoveryTest {
    @Test
    void recoveryDispatchesOnlyTheConfiguredBoundedBatch() {
        SavedProductRepository repository = mock(SavedProductRepository.class);
        ProductEnrichmentDispatcher dispatcher = mock(ProductEnrichmentDispatcher.class);
        ProductEnrichmentProperties properties = new ProductEnrichmentProperties();
        properties.setRecoveryBatchSize(2);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(repository.findRecoverableIds(any(), any(), anyInt(), any(Pageable.class)))
            .thenReturn(List.of(first, second));

        new ProductEnrichmentRecovery(repository, dispatcher, properties).recover();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findRecoverableIds(any(), any(), anyInt(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
        verify(dispatcher, times(1)).dispatch(first);
        verify(dispatcher, times(1)).dispatch(second);
    }
}

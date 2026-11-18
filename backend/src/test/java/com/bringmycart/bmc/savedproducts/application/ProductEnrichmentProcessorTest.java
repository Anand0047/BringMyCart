package com.bringmycart.bmc.savedproducts.application;

import java.util.Optional;
import java.util.UUID;

import com.bringmycart.bmc.retailer.Retailer;
import com.bringmycart.bmc.retailer.scrapedo.ScrapeDoProductMetadataProvider;
import com.bringmycart.bmc.savedproducts.domain.ProductMetadata;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductEnrichmentProcessorTest {
    @Test
    void preservesAxessoAsAmazonOnlyProvider() {
        UUID id = UUID.randomUUID();
        ProductEnrichmentStateService state = mock(ProductEnrichmentStateService.class);
        ProductMetadataProvider amazon = mock(ProductMetadataProvider.class);
        ScrapeDoProductMetadataProvider scrapeDo = mock(ScrapeDoProductMetadataProvider.class);
        when(state.claim(id)).thenReturn(Optional.of(new ProductEnrichmentStateService.EnrichmentJob(id,
            Retailer.AMAZON, "B0D2MW6L54", "B0D2MW6L54", "https://www.amazon.in/dp/B0D2MW6L54")));
        ProductMetadata metadata = new ProductMetadata("B0D2MW6L54", null, "Phone", null, null,
            null, null, "INR", null, null, null, null, null);
        when(amazon.fetch("B0D2MW6L54")).thenReturn(metadata);
        when(state.complete(id, metadata)).thenReturn(true);

        new ProductEnrichmentProcessor(state, amazon, scrapeDo).process(id);

        verify(amazon).fetch("B0D2MW6L54");
        verify(scrapeDo, never()).fetch(Retailer.AMAZON, "https://www.amazon.in/dp/B0D2MW6L54");
        verify(state).complete(id, metadata);
    }

    @Test
    void usesValidatedScrapeDoResultForNewRetailers() {
        UUID id = UUID.randomUUID();
        ProductEnrichmentStateService state = mock(ProductEnrichmentStateService.class);
        ProductMetadataProvider amazon = mock(ProductMetadataProvider.class);
        ScrapeDoProductMetadataProvider scrapeDo = mock(ScrapeDoProductMetadataProvider.class);
        String url = "https://www.myntra.com/shirt/brand/12345678/buy";
        when(state.claim(id)).thenReturn(Optional.of(new ProductEnrichmentStateService.EnrichmentJob(id,
            Retailer.MYNTRA, "12345678", null, url)));
        ProductMetadata metadata = new ProductMetadata(null, null, "Shirt", null, null, null, null,
            "INR", null, null, null, "UNKNOWN", null, null, java.util.List.of(), java.util.List.of(),
            java.util.List.of(), null, null, "SCRAPEDO");
        when(scrapeDo.fetch(Retailer.MYNTRA, url)).thenReturn(metadata);
        when(state.complete(id, metadata)).thenReturn(true);

        new ProductEnrichmentProcessor(state, amazon, scrapeDo).process(id);

        verify(scrapeDo).fetch(Retailer.MYNTRA, url);
        verify(amazon, never()).fetch("12345678");
        verify(state).complete(id, metadata);
    }

    @Test
    void fallsBackToExtensionMetadataWhenNewRetailerProviderFails() {
        UUID id = UUID.randomUUID();
        ProductEnrichmentStateService state = mock(ProductEnrichmentStateService.class);
        ProductMetadataProvider amazon = mock(ProductMetadataProvider.class);
        ScrapeDoProductMetadataProvider scrapeDo = mock(ScrapeDoProductMetadataProvider.class);
        when(state.claim(id)).thenReturn(Optional.of(new ProductEnrichmentStateService.EnrichmentJob(id,
            Retailer.FLIPKART, "MOBABCDE1234", null,
            "https://www.flipkart.com/phone/p/item123456?pid=MOBABCDE1234")));
        when(scrapeDo.fetch(Retailer.FLIPKART, "https://www.flipkart.com/phone/p/item123456?pid=MOBABCDE1234"))
            .thenThrow(new ProductMetadataProviderException(ProductMetadataProviderException.FailureType.RATE_LIMITED, "safe"));
        when(state.completeUsingExistingFallback(id)).thenReturn(true);

        new ProductEnrichmentProcessor(state, amazon, scrapeDo).process(id);

        verify(state).completeUsingExistingFallback(id);
        verify(state, never()).fail(id, com.bringmycart.bmc.savedproducts.domain.EnrichmentFailureCategory.RATE_LIMITED, true);
    }
}

package com.bringmycart.bmc.retailer.scrapedo;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.bringmycart.bmc.retailer.Retailer;
import com.bringmycart.bmc.retailer.RetailerProductUrlValidator;
import com.bringmycart.bmc.savedproducts.application.ProductMetadataProviderException;
import com.bringmycart.bmc.savedproducts.domain.ProductMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ScrapeDoProductMetadataProvider {
    private static final String ENDPOINT = "https://api.scrape.do/";
    private final RestClient client;
    private final ScrapeDoProperties properties;
    private final RetailerProductUrlValidator urlValidator;
    private final RetailerHtmlMetadataExtractor extractor;

    public ScrapeDoProductMetadataProvider(@Qualifier("scrapeDoRestClient") RestClient client,
                                           ScrapeDoProperties properties,
                                           RetailerProductUrlValidator urlValidator,
                                           RetailerHtmlMetadataExtractor extractor) {
        this.client = client;
        this.properties = properties;
        this.urlValidator = urlValidator;
        this.extractor = extractor;
    }

    public ProductMetadata fetch(Retailer retailer, String canonicalUrl) {
        if (!properties.isEnabled() || properties.getApiToken() == null || properties.getApiToken().isBlank()) {
            throw failure(ProductMetadataProviderException.FailureType.NOT_CONFIGURED);
        }
        var validated = urlValidator.validate(canonicalUrl);
        if (validated.retailer() != retailer || retailer == Retailer.AMAZON) {
            throw failure(ProductMetadataProviderException.FailureType.INVALID_RESPONSE);
        }
        String requestUri = ENDPOINT + "?token=" + encode(properties.getApiToken())
            + "&url=" + encode(validated.canonicalUrl()) + "&disableRetry=true&disableRedirection=true";
        try {
            ProviderBody body = client.get().uri(URI.create(requestUri)).exchange((request, response) -> {
                int status = response.getStatusCode().value();
                if (!response.getStatusCode().is2xxSuccessful()) throw failureForStatus(status);
                long declaredLength = response.getHeaders().getContentLength();
                if (declaredLength > properties.getMaxResponseBytes()) {
                    throw failure(ProductMetadataProviderException.FailureType.INVALID_RESPONSE);
                }
                byte[] bytes = response.getBody().readNBytes(properties.getMaxResponseBytes() + 1);
                if (bytes.length > properties.getMaxResponseBytes()) {
                    throw failure(ProductMetadataProviderException.FailureType.INVALID_RESPONSE);
                }
                return new ProviderBody(status, new String(bytes, StandardCharsets.UTF_8));
            });
            if (body.html().isBlank()) throw failure(ProductMetadataProviderException.FailureType.INVALID_RESPONSE);
            ProductMetadata parsed = extractor.extract(retailer, validated.productId(), body.html(), validated.canonicalUrl());
            return new ProductMetadata(null, null, parsed.title(), parsed.brand(), parsed.imageUrl(), parsed.initialPrice(),
                parsed.finalPrice(), parsed.currency(), parsed.discountPercentage(),
                parsed.rating(), parsed.reviewsCount(), parsed.availability(), Instant.now(), parsed.description(),
                parsed.imageUrls(), parsed.features(), parsed.categories(), parsed.specifications(), parsed.manufacturer(), "SCRAPEDO");
        } catch (ProductMetadataProviderException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new ProductMetadataProviderException(ProductMetadataProviderException.FailureType.TIMEOUT,
                "Metadata provider request timed out");
        } catch (IllegalArgumentException | RestClientException exception) {
            throw new ProductMetadataProviderException(ProductMetadataProviderException.FailureType.INVALID_RESPONSE,
                "Metadata provider response was invalid");
        }
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private ProductMetadataProviderException failureForStatus(int status) {
        var type = status == 401 || status == 403 ? ProductMetadataProviderException.FailureType.AUTHENTICATION
            : status == 429 ? ProductMetadataProviderException.FailureType.RATE_LIMITED
            : status >= 500 ? ProductMetadataProviderException.FailureType.REMOTE_FAILURE
            : ProductMetadataProviderException.FailureType.INVALID_RESPONSE;
        return new ProductMetadataProviderException(type, "Metadata provider request failed");
    }

    private ProductMetadataProviderException failure(ProductMetadataProviderException.FailureType type) {
        return new ProductMetadataProviderException(type, "Metadata provider request is unavailable");
    }

    private record ProviderBody(int status, String html) { }
}

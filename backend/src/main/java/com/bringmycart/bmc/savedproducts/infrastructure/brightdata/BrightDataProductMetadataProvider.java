package com.bringmycart.bmc.savedproducts.infrastructure.brightdata;

import java.util.List;
import java.util.Map;

import com.bringmycart.bmc.savedproducts.application.ProductMetadataProvider;
import com.bringmycart.bmc.savedproducts.domain.ProductMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class BrightDataProductMetadataProvider implements ProductMetadataProvider {
    private final RestClient restClient;
    private final BrightDataProperties properties;
    private final BrightDataProductMetadataMapper metadataMapper;

    public BrightDataProductMetadataProvider(@Qualifier("brightDataRestClient") RestClient restClient,
                                             BrightDataProperties properties,
                                             BrightDataProductMetadataMapper metadataMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.metadataMapper = metadataMapper;
    }

    @Override
    public ProductMetadata fetch(String asin) {
        if (!properties.isConfigured()) {
            throw failure(ProductMetadataProviderException.FailureType.NOT_CONFIGURED,
                "Product metadata provider is not configured");
        }

        try {
            List<BrightDataProductResponse> responses = restClient.post()
                .uri("/datasets/v3/scrape?dataset_id={datasetId}&format=json", properties.getDatasetId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .body(Map.of("input", List.of(Map.of("url", "https://www.amazon.in/dp/" + asin))))
                .retrieve()
                .onStatus(status -> status.value() == 401 || status.value() == 403,
                    (request, response) -> { throw failure(ProductMetadataProviderException.FailureType.AUTHENTICATION,
                        "Product metadata provider authentication failed"); })
                .onStatus(status -> status.value() == 429,
                    (request, response) -> { throw failure(ProductMetadataProviderException.FailureType.RATE_LIMITED,
                        "Product metadata provider rate limit reached"); })
                .onStatus(HttpStatusCode::is5xxServerError,
                    (request, response) -> { throw failure(ProductMetadataProviderException.FailureType.REMOTE_FAILURE,
                        "Product metadata provider is temporarily unavailable"); })
                .body(new ParameterizedTypeReference<>() { });

            if (responses == null || responses.isEmpty()) {
                throw failure(ProductMetadataProviderException.FailureType.INVALID_RESPONSE,
                    "Product metadata provider returned no product metadata");
            }
            BrightDataProductResponse matchingResponse = responses.stream()
                .filter(response -> metadataMapper.matchesRequestedAsin(asin, response))
                .findFirst()
                .orElseThrow(() -> failure(ProductMetadataProviderException.FailureType.INVALID_RESPONSE,
                    "Bright Data returned metadata for a different product"));
            return metadataMapper.map(asin, matchingResponse);
        } catch (ProductMetadataProviderException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw failure(ProductMetadataProviderException.FailureType.TIMEOUT,
                "Product metadata provider request failed", exception);
        } catch (RestClientException exception) {
            throw failure(ProductMetadataProviderException.FailureType.INVALID_RESPONSE,
                "Product metadata provider returned an invalid response", exception);
        }
    }

    private ProductMetadataProviderException failure(ProductMetadataProviderException.FailureType type, String message) {
        return new ProductMetadataProviderException(type, message);
    }

    private ProductMetadataProviderException failure(ProductMetadataProviderException.FailureType type, String message,
                                                      Throwable cause) {
        return new ProductMetadataProviderException(type, message, cause);
    }
}

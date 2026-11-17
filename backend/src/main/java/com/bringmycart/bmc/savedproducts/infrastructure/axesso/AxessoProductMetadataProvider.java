package com.bringmycart.bmc.savedproducts.infrastructure.axesso;

import java.util.Locale;

import com.bringmycart.bmc.savedproducts.application.ProductMetadataProvider;
import com.bringmycart.bmc.savedproducts.application.ProductMetadataProviderException;
import com.bringmycart.bmc.savedproducts.domain.ProductMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AxessoProductMetadataProvider implements ProductMetadataProvider {
    private static final Logger log = LoggerFactory.getLogger(AxessoProductMetadataProvider.class);
    private static final String PRODUCT_URL = "https://www.amazon.in/dp/%s";
    private final RestClient restClient;
    private final AxessoProperties properties;
    private final AxessoProductMetadataMapper mapper;
    private final AxessoMonthlyRequestQuota quota;
    private final ObjectMapper objectMapper;

    public AxessoProductMetadataProvider(@Qualifier("axessoRestClient") RestClient restClient,
                                         AxessoProperties properties,
                                         AxessoProductMetadataMapper mapper,
                                         AxessoMonthlyRequestQuota quota,
                                         ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.mapper = mapper;
        this.quota = quota;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProductMetadata fetch(String asin) {
        if (!properties.isConfigured()) {
            boolean keyPresent = properties.getApiKey() != null && !properties.getApiKey().isBlank();
            boolean hostPresent = properties.getHost() != null && !properties.getHost().isBlank();
            log.warn("Axesso invocation unavailable reason=configuration_missing rapidApiKeyPresent={} hostPresent={}",
                keyPresent, hostPresent);
            throw failure(ProductMetadataProviderException.FailureType.NOT_CONFIGURED,
                "Product metadata provider is not configured");
        }
        String normalizedAsin = asin == null ? "" : asin.trim().toUpperCase(Locale.ROOT);
        if (!normalizedAsin.matches("[A-Z0-9]{10}")) {
            throw failure(ProductMetadataProviderException.FailureType.INVALID_RESPONSE, "Invalid product identifier");
        }
        quota.reserveRequest();
        try {
            log.info("Axesso HTTP request starting method=GET endpoint=amazon-lookup-product");
            var response = restClient.get()
                .uri("/amz/amazon-lookup-product?url={url}", PRODUCT_URL.formatted(normalizedAsin))
                .header("x-rapidapi-key", properties.getApiKey())
                .header("x-rapidapi-host", properties.getHost())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, providerResponse) -> {
                    int status = providerResponse.getStatusCode().value();
                    ProductMetadataProviderException.FailureType type = failureType(status);
                    log.warn("Axesso HTTP response status={} failureType={}", status, type);
                    throw failure(type, safeMessage(type));
                })
                .toEntity(JsonNode.class);
            log.info("Axesso HTTP response received status={}", response.getStatusCode().value());
            AxessoProductResponse product = responseFrom(normalizedAsin, response.getBody());
            if (!mapper.matchesRequestedAsin(normalizedAsin, product)) {
                log.warn("Axesso response rejected reason=asin_mismatch_or_missing");
                throw failure(ProductMetadataProviderException.FailureType.INVALID_RESPONSE,
                    "Axesso returned metadata for a different product");
            }
            log.info("Axesso response ASIN validation passed");
            ProductMetadata metadata = mapper.map(normalizedAsin, product);
            log.info("Axesso metadata mapping completed titlePresent={} imagePresent={} pricePresent={} ratingPresent={} reviewCountPresent={} availabilityPresent={}",
                metadata.title() != null, metadata.imageUrl() != null, metadata.finalPrice() != null,
                metadata.rating() != null, metadata.reviewsCount() != null, metadata.availability() != null);
            return metadata;
        } catch (ProductMetadataProviderException exception) {
            log.warn("Axesso invocation failed failureType={} exceptionType={}", exception.getFailureType(),
                exception.getClass().getSimpleName());
            throw exception;
        } catch (ResourceAccessException exception) {
            log.warn("Axesso invocation failed failureType=TIMEOUT exceptionType={}", exception.getClass().getSimpleName());
            throw new ProductMetadataProviderException(ProductMetadataProviderException.FailureType.TIMEOUT,
                "Product metadata provider request failed", exception);
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Axesso invocation failed failureType=INVALID_RESPONSE exceptionType={}",
                exception.getClass().getSimpleName());
            throw new ProductMetadataProviderException(ProductMetadataProviderException.FailureType.INVALID_RESPONSE,
                "Product metadata provider returned an invalid response", exception);
        }
    }

    private ProductMetadataProviderException.FailureType failureType(int status) {
        if (status == 401 || status == 403) return ProductMetadataProviderException.FailureType.AUTHENTICATION;
        if (status == 429) return ProductMetadataProviderException.FailureType.RATE_LIMITED;
        if (status >= 500) return ProductMetadataProviderException.FailureType.REMOTE_FAILURE;
        return ProductMetadataProviderException.FailureType.INVALID_RESPONSE;
    }

    private String safeMessage(ProductMetadataProviderException.FailureType type) {
        return switch (type) {
            case AUTHENTICATION -> "Product metadata provider authentication failed";
            case RATE_LIMITED, QUOTA_EXHAUSTED -> "Product metadata provider request limit reached";
            case REMOTE_FAILURE -> "Product metadata provider is temporarily unavailable";
            default -> "Product metadata provider returned an invalid response";
        };
    }

    private AxessoProductResponse responseFrom(String requestedAsin, JsonNode body) {
        if (body != null && body.isArray()) {
            for (JsonNode item : body) {
                AxessoProductResponse candidate = responseFromObject(item);
                if (mapper.matchesRequestedAsin(requestedAsin, candidate)) return candidate;
            }
            return null;
        }
        return responseFromObject(body);
    }

    private AxessoProductResponse responseFromObject(JsonNode product) {
        if (product == null || !product.isObject()) return null;
        try {
            return objectMapper.treeToValue(product, AxessoProductResponse.class);
        } catch (Exception exception) {
            throw new ProductMetadataProviderException(ProductMetadataProviderException.FailureType.INVALID_RESPONSE,
                "Product metadata provider returned an invalid response", exception);
        }
    }

    private ProductMetadataProviderException failure(ProductMetadataProviderException.FailureType type, String message) {
        return new ProductMetadataProviderException(type, message);
    }
}

package com.bringmycart.bmc.savedproducts.infrastructure.brightdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

import com.bringmycart.bmc.savedproducts.domain.ProductMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class BrightDataProductMetadataMapper {
    private static final Pattern ASIN = Pattern.compile("^[A-Z0-9]{10}$");

    public ProductMetadata map(String requestedAsin, BrightDataProductResponse response) {
        if (!matchesRequestedAsin(requestedAsin, response)) {
            throw invalidResponse("Bright Data returned metadata for a different product");
        }
        String returnedAsin = normalizeAsin(response.asin());

        BigDecimal initialPrice = positiveMoney(response.initialPrice());
        BigDecimal finalPrice = positiveMoney(response.finalPrice());
        return new ProductMetadata(
            returnedAsin,
            normalizeAsin(response.parentAsin()),
            trimToNull(response.title()),
            trimToNull(response.brand()),
            trimToNull(response.imageUrl()),
            initialPrice,
            finalPrice,
            normalizeCurrency(response.currency()),
            calculateDiscount(initialPrice, finalPrice),
            rating(response.rating()),
            nonNegativeLong(response.reviewsCount()),
            trimToNull(response.availability()),
            parseTimestamp(response.timestamp())
        );
    }

    public boolean matchesRequestedAsin(String requestedAsin, BrightDataProductResponse response) {
        String normalizedRequestedAsin = normalizeAsin(requestedAsin);
        String returnedAsin = response == null ? null : normalizeAsin(response.asin());
        return normalizedRequestedAsin != null && normalizedRequestedAsin.equals(returnedAsin);
    }

    public BigDecimal calculateDiscount(BigDecimal initialPrice, BigDecimal finalPrice) {
        if (initialPrice == null || finalPrice == null || initialPrice.signum() <= 0 || finalPrice.signum() < 0
            || initialPrice.compareTo(finalPrice) <= 0) {
            return null;
        }
        return initialPrice.subtract(finalPrice).multiply(BigDecimal.valueOf(100))
            .divide(initialPrice, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal positiveMoney(JsonNode value) {
        BigDecimal parsed = decimal(value);
        return parsed == null || parsed.signum() < 0 ? null : parsed;
    }

    private BigDecimal rating(JsonNode value) {
        BigDecimal parsed = decimal(value);
        return parsed == null || parsed.signum() < 0 || parsed.compareTo(BigDecimal.valueOf(5)) > 0 ? null : parsed;
    }

    private Long nonNegativeLong(JsonNode value) {
        BigDecimal parsed = decimal(value);
        if (parsed == null || parsed.signum() < 0 || parsed.stripTrailingZeros().scale() > 0) {
            return null;
        }
        try {
            return parsed.longValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private BigDecimal decimal(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        String raw = value.asText().trim();
        if (raw.isEmpty()) {
            return null;
        }
        String normalized = raw.replace(",", "").replaceAll("[^0-9.-]", "");
        if (normalized.isBlank() || "-".equals(normalized) || ".".equals(normalized)) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeAsin(String asin) {
        String normalized = trimToNull(asin);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return ASIN.matcher(normalized).matches() ? normalized : null;
    }

    private String normalizeCurrency(String currency) {
        String normalized = trimToNull(currency);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return normalized.matches("^[A-Z]{3}$") ? normalized : null;
    }

    private Instant parseTimestamp(String timestamp) {
        String value = trimToNull(timestamp);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ProductMetadataProviderException invalidResponse(String message) {
        return new ProductMetadataProviderException(ProductMetadataProviderException.FailureType.INVALID_RESPONSE, message);
    }
}

package com.bringmycart.bmc.savedproducts.infrastructure.axesso;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

import com.bringmycart.bmc.savedproducts.application.ProductMetadataProviderException;
import com.bringmycart.bmc.savedproducts.domain.ProductMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class AxessoProductMetadataMapper {
    private static final Pattern ASIN = Pattern.compile("^[A-Z0-9]{10}$");
    private static final Pattern RATING = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)");

    public ProductMetadata map(String requestedAsin, AxessoProductResponse response) {
        if (!matchesRequestedAsin(requestedAsin, response)) {
            throw invalidResponse("Axesso returned metadata for a different product");
        }
        BigDecimal retailPrice = nonNegativeMoney(response.retailPrice());
        BigDecimal sellingPrice = nonNegativeMoney(response.price());
        return new ProductMetadata(
            normalizeAsin(response.asin()),
            normalizeAsin(response.parentAsin()),
            trimToNull(response.productTitle()),
            text(response.manufacturer()),
            imageUrl(response.mainImage(), response.imageUrlList()),
            retailPrice,
            sellingPrice,
            currency(response.currency()),
            calculateDiscount(retailPrice, sellingPrice),
            rating(response.productRating()),
            nonNegativeLong(response.countReview()),
            text(response.warehouseAvailability()),
            timestamp(response.timestamp())
        );
    }

    public boolean matchesRequestedAsin(String requestedAsin, AxessoProductResponse response) {
        String requested = normalizeAsin(requestedAsin);
        return requested != null && response != null && requested.equals(normalizeAsin(response.asin()));
    }

    private BigDecimal calculateDiscount(BigDecimal original, BigDecimal selling) {
        if (original == null || selling == null || original.signum() <= 0 || original.compareTo(selling) <= 0) {
            return null;
        }
        return original.subtract(selling).multiply(BigDecimal.valueOf(100))
            .divide(original, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nonNegativeMoney(JsonNode value) {
        BigDecimal amount = decimal(value);
        return amount == null || amount.signum() < 0 ? null : amount;
    }

    private BigDecimal rating(JsonNode value) {
        String raw = scalar(value);
        if (raw == null) return null;
        var match = RATING.matcher(raw);
        if (!match.find()) return null;
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(match.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
        return parsed == null || parsed.signum() < 0 || parsed.compareTo(BigDecimal.valueOf(5)) > 0 ? null : parsed;
    }

    private Long nonNegativeLong(JsonNode value) {
        BigDecimal parsed = decimal(value);
        if (parsed == null || parsed.signum() < 0 || parsed.stripTrailingZeros().scale() > 0) return null;
        try {
            return parsed.longValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private BigDecimal decimal(JsonNode value) {
        String raw = scalar(value);
        if (raw == null) return null;
        String normalized = raw.replace(",", "").replaceAll("[^0-9.-]", "");
        if (normalized.isBlank() || normalized.equals("-") || normalized.equals(".")) return null;
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String imageUrl(JsonNode mainImage, JsonNode imageList) {
        String primary = mainImage != null && mainImage.isObject() ? validImageUrl(text(mainImage.get("imageUrl"))) : null;
        if (primary != null) return primary;
        if (imageList != null && imageList.isArray()) {
            for (JsonNode image : imageList) {
                String candidate = image.isObject() ? text(image.get("imageUrl")) : text(image);
                candidate = validImageUrl(candidate);
                if (candidate != null) return candidate;
            }
        }
        return null;
    }

    private String validImageUrl(String value) {
        if (value == null) return null;
        try {
            java.net.URI uri = java.net.URI.create(value);
            String scheme = uri.getScheme();
            return uri.isAbsolute() && uri.getHost() != null && scheme != null
                && (scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")) ? value : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual() || node.isNumber() || node.isBoolean()) return trimToNull(node.asText());
        if (node.isObject()) {
            for (String key : new String[] {"value", "name", "availability", "manufacturer"}) {
                JsonNode nested = node.get(key);
                if (nested != null && nested.isValueNode()) return trimToNull(nested.asText());
            }
        }
        return null;
    }

    private String scalar(JsonNode node) {
        if (node == null || node.isNull() || !node.isValueNode()) return null;
        return trimToNull(node.asText());
    }

    private String normalizeAsin(String asin) {
        String value = trimToNull(asin);
        if (value == null) return null;
        value = value.toUpperCase(Locale.ROOT);
        return ASIN.matcher(value).matches() ? value : null;
    }

    private String currency(String currency) {
        String value = trimToNull(currency);
        if (value == null) return "INR"; // Requests are constructed only for the Amazon India marketplace.
        value = value.toUpperCase(Locale.ROOT);
        return value.matches("[A-Z]{3}") ? value : "INR";
    }

    private Instant timestamp(String timestamp) {
        String value = trimToNull(timestamp);
        if (value == null) return null;
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

package com.bringmycart.bmc.savedproducts.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.bringmycart.bmc.identity.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AmazonIndiaProductUrlValidator {
    private static final Set<String> SUPPORTED_HOSTS = Set.of("amazon.in", "www.amazon.in");
    private static final String ASIN_PATTERN = "[A-Za-z0-9]{10}";

    public ValidatedAmazonProductUrl validate(String submittedUrl) {
        if (submittedUrl == null || submittedUrl.isBlank()) {
            throw invalidUrl();
        }
        URI uri = parse(submittedUrl);
        validateOrigin(uri);

        String asin = extractAsin(uri.getRawPath());
        String canonicalUrl = "https://www.amazon.in/dp/" + asin;
        return new ValidatedAmazonProductUrl(submittedUrl, canonicalUrl, asin);
    }

    private URI parse(String submittedUrl) {
        try {
            return new URI(submittedUrl);
        } catch (URISyntaxException exception) {
            throw invalidUrl();
        }
    }

    private void validateOrigin(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())
            || uri.getHost() == null
            || uri.getUserInfo() != null
            || uri.getPort() != -1) {
            throw invalidUrl();
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_HOSTS.contains(host)) {
            throw invalidUrl();
        }
    }

    private String extractAsin(String rawPath) {
        if (rawPath == null) {
            throw invalidUrl();
        }

        List<String> parts = new ArrayList<>(List.of(rawPath.split("/")));
        parts.removeIf(String::isEmpty);
        List<String> normalized = parts.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();

        int dpIndex = -1;
        for (int index = 0; index < normalized.size() - 1; index++) {
            if ("dp".equals(normalized.get(index))) {
                dpIndex = index;
                break;
            }
        }
        String candidate;
        if (dpIndex >= 0) {
            if (!hasOnlyReferralSuffix(parts, dpIndex + 2)) {
                throw invalidUrl();
            }
            candidate = parts.get(dpIndex + 1);
        } else if (normalized.size() >= 3 && "gp".equals(normalized.get(0))
            && "product".equals(normalized.get(1))) {
            if (!hasOnlyReferralSuffix(parts, 3)) {
                throw invalidUrl();
            }
            candidate = parts.get(2);
        } else if (normalized.size() >= 4 && "gp".equals(normalized.get(0))
            && "aw".equals(normalized.get(1)) && "d".equals(normalized.get(2))) {
            if (!hasOnlyReferralSuffix(parts, 4)) {
                throw invalidUrl();
            }
            candidate = parts.get(3);
        } else {
            throw invalidUrl();
        }
        if (!candidate.matches(ASIN_PATTERN)) {
            throw invalidUrl();
        }
        return candidate.toUpperCase(Locale.ROOT);
    }

    private boolean hasOnlyReferralSuffix(List<String> parts, int suffixStart) {
        return suffixStart >= parts.size() || parts.get(suffixStart).toLowerCase(Locale.ROOT).startsWith("ref=");
    }

    private ApiException invalidUrl() {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_product_url",
            "Provide a supported HTTPS Amazon India product URL");
    }
}

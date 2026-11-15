package com.bringmycart.bmc.savedproducts.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bringmycart.bmc.identity.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AmazonIndiaProductUrlValidator {
    private static final Set<String> SUPPORTED_HOSTS = Set.of("amazon.in", "www.amazon.in");
    private static final Pattern DP_PATH = Pattern.compile("^/dp/([A-Za-z0-9]{10})/?$");
    private static final Pattern GP_PRODUCT_PATH = Pattern.compile("^/gp/product/([A-Za-z0-9]{10})/?$");

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
            || uri.getPort() != -1
            || uri.getFragment() != null) {
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

        Matcher dpMatcher = DP_PATH.matcher(rawPath);
        Matcher gpProductMatcher = GP_PRODUCT_PATH.matcher(rawPath);
        if (dpMatcher.matches()) {
            return dpMatcher.group(1).toUpperCase(Locale.ROOT);
        }
        if (gpProductMatcher.matches()) {
            return gpProductMatcher.group(1).toUpperCase(Locale.ROOT);
        }
        throw invalidUrl();
    }

    private ApiException invalidUrl() {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_product_url",
            "Provide a supported HTTPS Amazon India product URL");
    }
}

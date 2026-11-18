package com.bringmycart.bmc.retailer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.bringmycart.bmc.identity.exception.ApiException;
import com.bringmycart.bmc.savedproducts.validation.AmazonIndiaProductUrlValidator;
import com.bringmycart.bmc.savedproducts.validation.ValidatedAmazonProductUrl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RetailerProductUrlValidator {
    private static final Set<String> FLIPKART_HOSTS = Set.of("flipkart.com", "www.flipkart.com");
    private static final Set<String> MYNTRA_HOSTS = Set.of("myntra.com", "www.myntra.com");
    private final AmazonIndiaProductUrlValidator amazonValidator;

    public RetailerProductUrlValidator(AmazonIndiaProductUrlValidator amazonValidator) {
        this.amazonValidator = amazonValidator;
    }

    public ValidatedRetailerProductUrl validate(String submittedUrl) {
        URI uri = parse(submittedUrl);
        validateHttpsOrigin(uri);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("amazon.in") || host.equals("www.amazon.in")) {
            ValidatedAmazonProductUrl amazon = amazonValidator.validate(submittedUrl);
            return new ValidatedRetailerProductUrl(Retailer.AMAZON, amazon.asin(), amazon.originalUrl(),
                amazon.canonicalUrl(), amazon.asin());
        }
        if (FLIPKART_HOSTS.contains(host)) return flipkart(uri, submittedUrl);
        if (MYNTRA_HOSTS.contains(host)) return myntra(uri, submittedUrl);
        throw invalidUrl();
    }

    private ValidatedRetailerProductUrl flipkart(URI uri, String original) {
        List<String> segments = segments(uri.getRawPath());
        String pathProductId = null;
        for (int i = 0; i < segments.size() - 1; i++) {
            if (segments.get(i).equalsIgnoreCase("p")) {
                pathProductId = segments.get(i + 1);
                break;
            }
        }
        List<String> pids = queryParameters(uri.getRawQuery(), "pid");
        if (pids.size() > 1 || !pids.isEmpty() && !validId(pids.getFirst())) throw invalidUrl();
        String pid = pids.isEmpty() ? null : pids.getFirst();
        String identity = pid == null ? pathProductId : pid;
        if (!validId(identity)) throw invalidUrl();
        String canonical = "https://www.flipkart.com" + uri.getRawPath();
        if (validId(pid)) canonical += "?pid=" + pid;
        return new ValidatedRetailerProductUrl(Retailer.FLIPKART, identity, original, canonical, null);
    }

    private ValidatedRetailerProductUrl myntra(URI uri, String original) {
        List<String> segments = segments(uri.getRawPath());
        String productId = null;
        for (int i = segments.size() - 1; i >= 0; i--) {
            String part = segments.get(i);
            if (part.matches("[0-9]{4,20}") && (i == segments.size() - 1
                || segments.get(i + 1).equalsIgnoreCase("buy"))) {
                productId = part;
                break;
            }
        }
        if (productId == null) throw invalidUrl();
        String canonical = "https://www.myntra.com/" + String.join("/", segments);
        return new ValidatedRetailerProductUrl(Retailer.MYNTRA, productId, original, canonical, null);
    }

    private URI parse(String value) {
        if (value == null || value.isBlank() || value.length() > 2048) throw invalidUrl();
        try { return new URI(value); } catch (URISyntaxException exception) { throw invalidUrl(); }
    }

    private void validateHttpsOrigin(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
            || uri.getPort() != -1 || uri.getRawPath() == null || uri.getRawPath().contains("\\")) throw invalidUrl();
    }

    private List<String> segments(String path) {
        return java.util.Arrays.stream(path.split("/"))
            .filter(segment -> !segment.isBlank())
            .toList();
    }

    private List<String> queryParameters(String rawQuery, String name) {
        if (rawQuery == null) return List.of();
        List<String> values = new java.util.ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0 && pair.substring(0, equals).equalsIgnoreCase(name)) {
                try { values.add(java.net.URLDecoder.decode(pair.substring(equals + 1), java.nio.charset.StandardCharsets.UTF_8)); }
                catch (IllegalArgumentException exception) { return List.of(""); }
            }
        }
        return values;
    }

    private boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{5,80}");
    }

    private ApiException invalidUrl() {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_product_url", "Provide a supported HTTPS retailer product URL");
    }
}

package com.bringmycart.bmc.retailer.scrapedo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bringmycart.bmc.retailer.Retailer;
import com.bringmycart.bmc.savedproducts.domain.ProductMetadata;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class RetailerHtmlMetadataExtractor {
    private static final Pattern MONEY = Pattern.compile("(?:₹|Rs\\.?\\s*)?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)");
    private final ObjectMapper mapper;

    public RetailerHtmlMetadataExtractor(ObjectMapper mapper) { this.mapper = mapper; }

    public ProductMetadata extract(Retailer retailer, String productId, String html, String pageUrl) {
        Document doc = Jsoup.parse(html, pageUrl);
        JsonNode product = findProduct(doc);
        String returnedId = firstText(product, "sku", "productID", "productId");
        if (returnedId != null && !returnedId.equalsIgnoreCase(productId)) {
            throw new IllegalArgumentException("Structured product identifier did not match the requested item");
        }
        String title = text(product, "name");
        if (title == null) title = meta(doc, "og:title");
        if (title == null) title = retailerTitle(doc, retailer);
        if (title != null && title.matches("(?i).*(captcha|verify (that )?you are human|access denied|robot check|just a moment).*")) {
            title = null;
        }

        List<String> images = jsonImages(product.get("image"));
        addUrl(images, meta(doc, "og:image"), doc.baseUri());
        if (images.isEmpty()) addUrl(images, retailerImage(doc, retailer), doc.baseUri());
        String image = images.isEmpty() ? null : images.getFirst();

        JsonNode offers = first(product.get("offers"));
        BigDecimal price = money(firstText(offers, "price", "lowPrice", "salePrice"));
        String amount = meta(doc, "product:price:amount");
        if (price == null) price = money(amount);
        if (price == null) price = money(retailerPrice(doc, retailer));
        BigDecimal original = money(firstText(offers, "highPrice", "listPrice", "retailPrice"));
        BigDecimal rating = decimal(firstText(first(product.get("aggregateRating")), "ratingValue"), BigDecimal.ZERO, BigDecimal.valueOf(5));
        Long reviews = longValue(firstText(first(product.get("aggregateRating")), "reviewCount", "ratingCount"));
        String availability = availability(firstText(offers, "availability"));
        if (availability == null) availability = availability(meta(doc, "product:availability"));
        if (availability == null) availability = "UNKNOWN";
        String brand = text(first(product.get("brand")), "name");
        if (brand == null) brand = scalar(product.get("brand"));
        String description = text(product, "description");
        if (description == null) description = meta(doc, "og:description");
        if (description == null) description = doc.selectFirst("meta[name=description]") == null ? null
            : doc.selectFirst("meta[name=description]").attr("content");

        List<String> categories = categories(product.get("category"));
        List<String> features = features(product.get("additionalProperty"));
        JsonNode specs = specifications(product.get("additionalProperty"));
        String currency = firstText(offers, "priceCurrency");
        if (currency == null && price != null) currency = "INR";

        return new ProductMetadata(null, null, clean(title), clean(brand), image, original, price,
            normalizeCurrency(currency), discount(original, price), rating, reviews, availability, null,
            clean(description), images, features, categories, specs, text(product, "manufacturer"), "SCRAPEDO");
    }

    private JsonNode findProduct(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            String raw = script.data();
            if (raw.length() > 256_000) continue;
            try {
                JsonNode root = mapper.readTree(raw);
                JsonNode found = findProductNode(root);
                if (found != null) return found;
            } catch (Exception strictParseFailure) {
                // Some Myntra Product JSON-LD embeds review text with literal line breaks inside a
                // quoted JSON string. Retry only with Jackson's unescaped-control-character feature;
                // all other JSON rules remain strict and malformed blocks are still skipped safely.
                try {
                    JsonNode root = mapper.reader()
                        .with(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
                        .readTree(raw);
                    JsonNode found = findProductNode(root);
                    if (found != null) return found;
                } catch (Exception ignored) {
                    // One malformed JSON-LD block must not discard other metadata sources.
                }
            }
        }
        return mapper.createObjectNode();
    }

    private JsonNode findProductNode(JsonNode node) {
        if (node == null) return null;
        if (node.isArray()) for (JsonNode item : node) { JsonNode found = findProductNode(item); if (found != null) return found; }
        if (node.isObject()) {
            JsonNode typeNode = node.get("@type");
            String type = scalar(typeNode);
            if ("Product".equalsIgnoreCase(type) || typeNode != null && typeNode.isArray()
                && java.util.stream.StreamSupport.stream(typeNode.spliterator(), false)
                    .map(this::scalar).anyMatch("Product"::equalsIgnoreCase)) return node;
            JsonNode graph = findProductNode(node.get("@graph"));
            if (graph != null) return graph;
        }
        return null;
    }

    private List<String> jsonImages(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        if (node != null) {
            if (node.isArray()) node.forEach(item -> addUrl(values, scalar(item), null));
            else addUrl(values, scalar(node), null);
        }
        return new ArrayList<>(values);
    }

    private void addUrl(java.util.Collection<String> out, String raw, String base) {
        if (raw == null || raw.isBlank()) return;
        try {
            java.net.URI uri = base == null ? java.net.URI.create(raw) : java.net.URI.create(base).resolve(raw);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null) out.add(uri.toString());
        } catch (IllegalArgumentException ignored) { }
    }

    private List<String> categories(JsonNode node) {
        String raw = scalar(node);
        if (raw == null) return List.of();
        return java.util.Arrays.stream(raw.split("[>/]")).map(String::trim).filter(v -> !v.isBlank()).toList();
    }

    private List<String> features(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) for (JsonNode item : node) {
            String value = text(item, "value");
            if (value == null) value = scalar(item);
            if (value != null) result.add(clean(value));
        }
        return result.stream().limit(30).toList();
    }

    private JsonNode specifications(JsonNode node) {
        if (node == null || !node.isArray()) return null;
        ArrayNode result = mapper.createArrayNode();
        for (JsonNode item : node) {
            String name = text(item, "name"); String value = text(item, "value");
            if (name != null && value != null) {
                ObjectNode pair = mapper.createObjectNode(); pair.put("name", clean(name)); pair.put("value", clean(value)); result.add(pair);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private String retailerTitle(Document doc, Retailer retailer) {
        Element element = retailer == Retailer.FLIPKART ? doc.selectFirst("span.B_NuCI, span.VU-ZEz") : doc.selectFirst("h1.pdp-title, h1.pdp-name");
        if (element != null) return element.text();
        return doc.title();
    }

    private String retailerImage(Document doc, Retailer retailer) {
        Element image = retailer == Retailer.FLIPKART ? doc.selectFirst("img._396cs4, img.DByuf4") : doc.selectFirst(".image-grid-image img, img.img-responsive");
        return image == null ? null : image.absUrl("src");
    }

    private String retailerPrice(Document doc, Retailer retailer) {
        Element price = retailer == Retailer.FLIPKART ? doc.selectFirst("div._30jeq3, div.Nx9bqj") : doc.selectFirst("span.pdp-price strong, .pdp-price");
        return price == null ? null : price.text();
    }

    private String meta(Document doc, String property) {
        Element element = doc.selectFirst("meta[property=\"" + property + "\"]");
        return element == null ? null : element.attr("content");
    }

    private BigDecimal money(String value) {
        if (value == null) return null;
        Matcher matcher = MONEY.matcher(value.replace("\u00a0", " "));
        if (!matcher.find()) return null;
        return decimal(matcher.group(1).replace(",", ""), new BigDecimal("0.01"), new BigDecimal("99999999.99"));
    }

    private BigDecimal decimal(String value, BigDecimal min, BigDecimal max) {
        try { BigDecimal n = new BigDecimal(value.trim()); return n.compareTo(min) >= 0 && n.compareTo(max) <= 0 ? n : null; }
        catch (Exception ignored) { return null; }
    }

    private Long longValue(String value) {
        try { long n = Long.parseLong(value); return n >= 0 ? n : null; } catch (Exception ignored) { return null; }
    }

    private String availability(String value) {
        if (value == null) return null;
        String s = value.toLowerCase(Locale.ROOT);
        if (s.contains("outofstock") || s.contains("out of stock")) return "OUT_OF_STOCK";
        if (s.contains("instock") || s.contains("in stock")) return "IN_STOCK";
        return "UNKNOWN";
    }

    private BigDecimal discount(BigDecimal original, BigDecimal current) {
        if (original == null || current == null || original.compareTo(current) <= 0) return null;
        return original.subtract(current).multiply(BigDecimal.valueOf(100)).divide(original, 2, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z]{3}") ? normalized : null;
    }
    private JsonNode first(JsonNode node) { return node != null && node.isArray() ? node.isEmpty() ? null : node.get(0) : node; }
    private String firstText(JsonNode node, String... fields) { for (String field : fields) { String value = text(node, field); if (value != null) return value; } return null; }
    private String text(JsonNode node, String key) { return node == null ? null : scalar(node.get(key)); }
    private String scalar(JsonNode node) { return node != null && node.isValueNode() && !node.isNull() ? clean(node.asText()) : null; }
    private String clean(String value) { return value == null ? null : org.jsoup.Jsoup.parseBodyFragment(value).text().trim(); }
}

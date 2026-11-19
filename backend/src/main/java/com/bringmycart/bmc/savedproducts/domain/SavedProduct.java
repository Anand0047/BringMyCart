package com.bringmycart.bmc.savedproducts.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.bringmycart.bmc.identity.domain.UserAccount;
import com.bringmycart.bmc.retailer.Retailer;
import com.bringmycart.bmc.retailer.RetailerAttributeConverter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "saved_products")
public class SavedProduct {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Convert(converter = RetailerAttributeConverter.class)
    @Column(nullable = false, length = 16)
    private Retailer retailer;

    @Column(name = "retailer_product_id", nullable = false, length = 80)
    private String retailerProductId;

    @Column(name = "variant_key", nullable = false, length = 100)
    private String variantKey = "";

    @Column(name = "variant_label", length = 255)
    private String variantLabel;

    @Column(length = 10)
    private String asin;

    @Column(name = "parent_asin", length = 10)
    private String parentAsin;

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(name = "canonical_url", nullable = false, columnDefinition = "TEXT")
    private String canonicalUrl;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String brand;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "product_description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_images", columnDefinition = "jsonb")
    private JsonNode imageUrls;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_features", columnDefinition = "jsonb")
    private JsonNode features;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_categories", columnDefinition = "jsonb")
    private JsonNode categories;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_specifications", columnDefinition = "jsonb")
    private JsonNode specifications;

    @Column(length = 255)
    private String manufacturer;

    @Column(name = "metadata_source", nullable = false, length = 24)
    private String metadataSource;

    @Column(name = "initial_price", precision = 12, scale = 2)
    private BigDecimal initialPrice;

    @Column(name = "final_price", precision = 12, scale = 2)
    private BigDecimal finalPrice;

    @Column(length = 3)
    private String currency;

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(name = "reviews_count")
    private Long reviewsCount;

    @Column(length = 100)
    private String availability;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrichment_status", nullable = false, length = 20)
    private EnrichmentStatus enrichmentStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review_summary", columnDefinition = "jsonb")
    private JsonNode reviewSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review_topics", columnDefinition = "jsonb")
    private JsonNode reviewTopics;

    @Column(name = "metadata_fetched_at")
    private Instant metadataFetchedAt;

    @Column(name = "provider_fetched_at")
    private Instant providerFetchedAt;

    @Column(name = "enrichment_requested_at")
    private Instant enrichmentRequestedAt;

    @Column(name = "enrichment_started_at")
    private Instant enrichmentStartedAt;

    @Column(name = "enrichment_attempts", nullable = false)
    private int enrichmentAttempts;

    @Column(name = "enrichment_error_category", length = 50)
    private String enrichmentErrorCategory;

    @Column(name = "enrichment_next_attempt_at")
    private Instant enrichmentNextAttemptAt;

    @Column(name = "last_manual_refresh_requested_at")
    private Instant lastManualRefreshRequestedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SavedProduct() {
    }

    public SavedProduct(UserAccount user, String asin, String originalUrl, String canonicalUrl) {
        this(user, Retailer.AMAZON, asin, asin, originalUrl, canonicalUrl);
    }

    public SavedProduct(UserAccount user, Retailer retailer, String retailerProductId, String asin,
                        String originalUrl, String canonicalUrl) {
        this(user, retailer, retailerProductId, asin, originalUrl, canonicalUrl, null, null);
    }

    public SavedProduct(UserAccount user, Retailer retailer, String retailerProductId, String asin,
                        String originalUrl, String canonicalUrl, String variantKey, String variantLabel) {
        this.user = user;
        this.retailer = retailer;
        this.retailerProductId = retailerProductId;
        this.asin = asin;
        this.originalUrl = originalUrl;
        this.canonicalUrl = canonicalUrl;
        this.variantKey = variantKey == null ? "" : variantKey;
        this.variantLabel = variantLabel;
        this.currency = retailer == Retailer.AMAZON ? "INR" : null;
        this.metadataSource = "URL_ONLY";
        this.enrichmentStatus = EnrichmentStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void completeEnrichment(ProductMetadata metadata, Instant completedAt) {
        if (metadata.parentAsin() != null) parentAsin = metadata.parentAsin();
        if (metadata.title() != null) title = metadata.title();
        if (metadata.brand() != null) brand = metadata.brand();
        if (metadata.imageUrl() != null) imageUrl = metadata.imageUrl();
        if (metadata.initialPrice() != null) initialPrice = metadata.initialPrice();
        if (metadata.finalPrice() != null) finalPrice = metadata.finalPrice();
        if (metadata.currency() != null) {
            currency = metadata.currency();
        }
        if (metadata.discountPercentage() != null) discountPercentage = metadata.discountPercentage();
        if (metadata.rating() != null) rating = metadata.rating();
        if (metadata.reviewsCount() != null) reviewsCount = metadata.reviewsCount();
        if (metadata.availability() != null) availability = metadata.availability();
        description = metadata.description() != null ? metadata.description() : description;
        imageUrls = metadata.imageUrls() != null && !metadata.imageUrls().isEmpty() ?
            toJsonArray(metadata.imageUrls()) : imageUrls;
        features = metadata.features() != null && !metadata.features().isEmpty() ?
            toJsonArray(metadata.features()) : features;
        categories = metadata.categories() != null && !metadata.categories().isEmpty() ?
            new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(metadata.categories()) : categories;
        specifications = metadata.specifications() != null ? metadata.specifications() : specifications;
        manufacturer = metadata.manufacturer() != null ? metadata.manufacturer() : manufacturer;
        providerFetchedAt = metadata.providerFetchedAt();
        metadataFetchedAt = completedAt;
        if (metadata.source() != null) metadataSource = metadata.source();
        enrichmentStatus = EnrichmentStatus.COMPLETED;
        enrichmentErrorCategory = null;
        enrichmentNextAttemptAt = null;
    }

    public void failEnrichment(EnrichmentFailureCategory category, Instant nextAttemptAt) {
        enrichmentStatus = EnrichmentStatus.FAILED;
        enrichmentErrorCategory = category.name();
        enrichmentNextAttemptAt = nextAttemptAt;
    }

    public void applyClientMetadata(String title, String brand, String imageUrl, BigDecimal price,
                                    String currency, String availability) {
        this.title = title;
        this.brand = brand;
        this.imageUrl = imageUrl;
        this.finalPrice = price;
        this.currency = currency == null && price != null ? "INR" : currency;
        this.availability = availability;
        this.metadataSource = "EXTENSION";
    }

    public boolean hasFallbackMetadata() {
        return "EXTENSION".equals(metadataSource)
            && title != null && !title.isBlank() && (imageUrl != null || finalPrice != null);
    }

    public void completeUsingExistingMetadata(Instant completedAt) {
        metadataFetchedAt = completedAt;
        metadataSource = "EXTENSION";
        enrichmentStatus = EnrichmentStatus.COMPLETED;
        enrichmentErrorCategory = null;
        enrichmentNextAttemptAt = null;
    }

    private com.fasterxml.jackson.databind.JsonNode toJsonArray(java.util.List<String> values) {
        var array = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        values.forEach(array::add);
        return array;
    }

    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public String getAsin() { return asin; }
    public Retailer getRetailer() { return retailer; }
    public String getRetailerProductId() { return retailerProductId; }
    public String getVariantKey() { return variantKey; }
    public String getVariantLabel() { return variantLabel; }
    public String getParentAsin() { return parentAsin; }
    public String getOriginalUrl() { return originalUrl; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public String getTitle() { return title; }
    public String getBrand() { return brand; }
    public String getImageUrl() { return imageUrl; }
    public String getDescription() { return description; }
    public JsonNode getImageUrls() { return imageUrls; }
    public JsonNode getFeatures() { return features; }
    public JsonNode getCategories() { return categories; }
    public JsonNode getSpecifications() { return specifications; }
    public String getManufacturer() { return manufacturer; }
    public String getMetadataSource() { return metadataSource; }
    public BigDecimal getInitialPrice() { return initialPrice; }
    public BigDecimal getFinalPrice() { return finalPrice; }
    public String getCurrency() { return currency; }
    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public BigDecimal getRating() { return rating; }
    public Long getReviewsCount() { return reviewsCount; }
    public String getAvailability() { return availability; }
    public EnrichmentStatus getEnrichmentStatus() { return enrichmentStatus; }
    public JsonNode getReviewSummary() { return reviewSummary; }
    public JsonNode getReviewTopics() { return reviewTopics; }
    public Instant getMetadataFetchedAt() { return metadataFetchedAt; }
    public Instant getProviderFetchedAt() { return providerFetchedAt; }
    public Instant getEnrichmentRequestedAt() { return enrichmentRequestedAt; }
    public Instant getEnrichmentStartedAt() { return enrichmentStartedAt; }
    public int getEnrichmentAttempts() { return enrichmentAttempts; }
    public String getEnrichmentErrorCategory() { return enrichmentErrorCategory; }
    public Instant getEnrichmentNextAttemptAt() { return enrichmentNextAttemptAt; }
    public Instant getLastManualRefreshRequestedAt() { return lastManualRefreshRequestedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

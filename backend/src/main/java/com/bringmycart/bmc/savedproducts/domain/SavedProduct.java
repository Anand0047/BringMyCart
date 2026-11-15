package com.bringmycart.bmc.savedproducts.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.bringmycart.bmc.identity.domain.UserAccount;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
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

    @Column(nullable = false, length = 10)
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

    @Column(name = "initial_price", precision = 12, scale = 2)
    private BigDecimal initialPrice;

    @Column(name = "final_price", precision = 12, scale = 2)
    private BigDecimal finalPrice;

    @Column(nullable = false, length = 3)
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
        this.user = user;
        this.asin = asin;
        this.originalUrl = originalUrl;
        this.canonicalUrl = canonicalUrl;
        this.currency = "INR";
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
        parentAsin = metadata.parentAsin();
        title = metadata.title();
        brand = metadata.brand();
        imageUrl = metadata.imageUrl();
        initialPrice = metadata.initialPrice();
        finalPrice = metadata.finalPrice();
        if (metadata.currency() != null) {
            currency = metadata.currency();
        }
        discountPercentage = metadata.discountPercentage();
        rating = metadata.rating();
        reviewsCount = metadata.reviewsCount();
        availability = metadata.availability();
        providerFetchedAt = metadata.providerFetchedAt();
        metadataFetchedAt = completedAt;
        enrichmentStatus = EnrichmentStatus.COMPLETED;
        enrichmentErrorCategory = null;
        enrichmentNextAttemptAt = null;
    }

    public void failEnrichment(EnrichmentFailureCategory category, Instant nextAttemptAt) {
        enrichmentStatus = EnrichmentStatus.FAILED;
        enrichmentErrorCategory = category.name();
        enrichmentNextAttemptAt = nextAttemptAt;
    }

    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public String getAsin() { return asin; }
    public String getParentAsin() { return parentAsin; }
    public String getOriginalUrl() { return originalUrl; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public String getTitle() { return title; }
    public String getBrand() { return brand; }
    public String getImageUrl() { return imageUrl; }
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

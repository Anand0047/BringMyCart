package com.bringmycart.bmc.savedproducts.application;

import java.util.UUID;
import java.time.Instant;

import com.bringmycart.bmc.identity.domain.UserAccount;
import com.bringmycart.bmc.identity.exception.ApiException;
import com.bringmycart.bmc.identity.infrastructure.UserAccountRepository;
import com.bringmycart.bmc.savedproducts.domain.SavedProduct;
import com.bringmycart.bmc.savedproducts.infrastructure.SavedProductRepository;
import com.bringmycart.bmc.savedproducts.validation.AmazonIndiaProductUrlValidator;
import com.bringmycart.bmc.savedproducts.validation.ValidatedAmazonProductUrl;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class SavedProductService {
    private final SavedProductRepository savedProductRepository;
    private final UserAccountRepository userAccountRepository;
    private final AmazonIndiaProductUrlValidator urlValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final ProductEnrichmentProperties enrichmentProperties;

    public SavedProductService(SavedProductRepository savedProductRepository,
                               UserAccountRepository userAccountRepository,
                               AmazonIndiaProductUrlValidator urlValidator,
                               ApplicationEventPublisher eventPublisher,
                               ProductEnrichmentProperties enrichmentProperties) {
        this.savedProductRepository = savedProductRepository;
        this.userAccountRepository = userAccountRepository;
        this.urlValidator = urlValidator;
        this.eventPublisher = eventPublisher;
        this.enrichmentProperties = enrichmentProperties;
    }

    @Transactional
    public SavedProduct save(UUID authenticatedUserId, String submittedUrl) {
        ValidatedAmazonProductUrl productUrl = urlValidator.validate(submittedUrl);
        UserAccount user = currentUser(authenticatedUserId);

        if (savedProductRepository.existsByUser_IdAndAsin(user.getId(), productUrl.asin())) {
            throw duplicateProduct();
        }

        try {
            SavedProduct savedProduct = savedProductRepository.saveAndFlush(new SavedProduct(
                user, productUrl.asin(), productUrl.originalUrl(), productUrl.canonicalUrl()));
            eventPublisher.publishEvent(new SavedProductCreatedEvent(savedProduct.getId()));
            return savedProduct;
        } catch (DataIntegrityViolationException exception) {
            // The constraint remains authoritative when simultaneous requests race past the pre-check.
            throw duplicateProduct();
        }
    }

    @Transactional(readOnly = true)
    public Page<SavedProduct> list(UUID authenticatedUserId, Pageable pageable) {
        currentUser(authenticatedUserId);
        return savedProductRepository.findAllByUser_Id(authenticatedUserId, pageable);
    }

    @Transactional
    public void delete(UUID authenticatedUserId, UUID savedProductId) {
        SavedProduct product = savedProductRepository.findByIdAndUser_Id(savedProductId, authenticatedUserId)
            .orElseThrow(this::savedProductNotFound);
        savedProductRepository.delete(product);
    }

    @Transactional
    public void requestManualRefresh(UUID authenticatedUserId, UUID savedProductId) {
        Instant now = Instant.now();
        int reserved = savedProductRepository.reserveManualRefresh(savedProductId, authenticatedUserId, now,
            now.minus(enrichmentProperties.getManualRefreshCooldown()));
        if (reserved == 1) {
            eventPublisher.publishEvent(new SavedProductRefreshRequestedEvent(savedProductId));
            return;
        }

        SavedProduct product = savedProductRepository.findByIdAndUser_Id(savedProductId, authenticatedUserId)
            .orElseThrow(this::savedProductNotFound);
        if (product.getEnrichmentStatus() == com.bringmycart.bmc.savedproducts.domain.EnrichmentStatus.PENDING
            || product.getEnrichmentStatus() == com.bringmycart.bmc.savedproducts.domain.EnrichmentStatus.PROCESSING) {
            throw new ApiException(HttpStatus.CONFLICT, "enrichment_in_progress", "Product enrichment is already in progress");
        }
        throw new ApiException(HttpStatus.CONFLICT, "refresh_cooldown", "Manual refresh is temporarily unavailable");
    }

    private UserAccount currentUser(UUID userId) {
        return userAccountRepository.findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "authentication_required", "Authentication required"));
    }

    private ApiException duplicateProduct() {
        return new ApiException(HttpStatus.CONFLICT, "duplicate_saved_product", "This product is already saved");
    }

    private ApiException savedProductNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "saved_product_not_found", "Saved product not found");
    }
}

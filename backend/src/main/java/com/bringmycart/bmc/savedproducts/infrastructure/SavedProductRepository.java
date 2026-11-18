package com.bringmycart.bmc.savedproducts.infrastructure;

import java.util.UUID;
import java.time.Instant;
import java.util.List;

import com.bringmycart.bmc.savedproducts.domain.SavedProduct;
import com.bringmycart.bmc.retailer.Retailer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface SavedProductRepository extends JpaRepository<SavedProduct, UUID> {
    boolean existsByUser_IdAndAsin(UUID userId, String asin);

    boolean existsByUser_IdAndRetailerAndRetailerProductId(UUID userId, Retailer retailer, String retailerProductId);

    Optional<SavedProduct> findByIdAndUser_Id(UUID id, UUID userId);

    Page<SavedProduct> findAllByUser_Id(UUID userId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update SavedProduct product
        set product.enrichmentStatus = com.bringmycart.bmc.savedproducts.domain.EnrichmentStatus.PROCESSING,
            product.enrichmentStartedAt = :now,
            product.enrichmentAttempts = product.enrichmentAttempts + 1,
            product.enrichmentErrorCategory = null,
            product.updatedAt = :now
        where product.id = :id
          and product.enrichmentAttempts < :maxAttempts
          and (
            product.enrichmentStatus = com.bringmycart.bmc.savedproducts.domain.EnrichmentStatus.PENDING
            or (product.enrichmentStatus = com.bringmycart.bmc.savedproducts.domain.EnrichmentStatus.FAILED
                and product.enrichmentNextAttemptAt is not null and product.enrichmentNextAttemptAt <= :now)
            or (product.enrichmentStatus = com.bringmycart.bmc.savedproducts.domain.EnrichmentStatus.PROCESSING
                and product.enrichmentStartedAt is not null and product.enrichmentStartedAt <= :staleBefore)
          )
        """)
    int claimForEnrichment(@Param("id") UUID id, @Param("now") Instant now,
                           @Param("staleBefore") Instant staleBefore, @Param("maxAttempts") int maxAttempts);

    @Query("""
        select product.id from SavedProduct product
        where product.enrichmentAttempts < :maxAttempts
          and (
            (product.enrichmentStatus = com.bringmycart.bmc.savedproducts.domain.EnrichmentStatus.PENDING
                and product.createdAt <= :staleBefore)
            or (product.enrichmentStatus = com.bringmycart.bmc.savedproducts.domain.EnrichmentStatus.PROCESSING
                and product.enrichmentStartedAt is not null and product.enrichmentStartedAt <= :staleBefore)
            or (product.enrichmentStatus = com.bringmycart.bmc.savedproducts.domain.EnrichmentStatus.FAILED
                and product.enrichmentNextAttemptAt is not null and product.enrichmentNextAttemptAt <= :now)
          )
        order by product.createdAt asc
        """)
    List<UUID> findRecoverableIds(@Param("now") Instant now, @Param("staleBefore") Instant staleBefore,
                                  @Param("maxAttempts") int maxAttempts, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update SavedProduct product
        set product.enrichmentStatus = com.bringmycart.bmc.savedproducts.domain.EnrichmentStatus.PENDING,
            product.enrichmentRequestedAt = :now,
            product.enrichmentStartedAt = null,
            product.enrichmentAttempts = 0,
            product.enrichmentErrorCategory = null,
            product.enrichmentNextAttemptAt = null,
            product.lastManualRefreshRequestedAt = :now,
            product.updatedAt = :now
        where product.id = :id
          and product.user.id = :userId
          and product.enrichmentStatus in (
            com.bringmycart.bmc.savedproducts.domain.EnrichmentStatus.COMPLETED,
            com.bringmycart.bmc.savedproducts.domain.EnrichmentStatus.FAILED
          )
          and (product.lastManualRefreshRequestedAt is null
               or product.lastManualRefreshRequestedAt <= :cooldownBefore)
        """)
    int reserveManualRefresh(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now,
                             @Param("cooldownBefore") Instant cooldownBefore);
}

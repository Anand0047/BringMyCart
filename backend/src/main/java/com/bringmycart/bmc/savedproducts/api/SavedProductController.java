package com.bringmycart.bmc.savedproducts.api;

import java.net.URI;
import java.util.UUID;

import com.bringmycart.bmc.savedproducts.api.dto.SaveSavedProductRequest;
import com.bringmycart.bmc.savedproducts.api.dto.SavedProductResponse;
import com.bringmycart.bmc.savedproducts.api.dto.PageResponse;
import com.bringmycart.bmc.savedproducts.application.SavedProductService;
import com.bringmycart.bmc.identity.exception.ApiException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/saved-products")
public class SavedProductController {
    private static final int MAX_PAGE_SIZE = 100;
    private final SavedProductService savedProductService;

    public SavedProductController(SavedProductService savedProductService) {
        this.savedProductService = savedProductService;
    }

    @PostMapping
    public ResponseEntity<SavedProductResponse> save(@AuthenticationPrincipal Jwt jwt,
                                                     @Valid @RequestBody SaveSavedProductRequest request) {
        SavedProductResponse response = SavedProductResponse.from(
            savedProductService.save(userId(jwt), request.url(), request.metadata()));
        return ResponseEntity.created(URI.create("/api/v1/saved-products/" + response.id())).body(response);
    }

    @GetMapping
    public PageResponse<SavedProductResponse> list(@AuthenticationPrincipal Jwt jwt,
                                           @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
                                           Pageable pageable) {
        Pageable boundedPageable = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.from(savedProductService.list(userId(jwt), boundedPageable), SavedProductResponse::from);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        savedProductService.delete(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/refresh")
    public ResponseEntity<Void> refresh(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        savedProductService.requestManualRefresh(userId(jwt), id);
        return ResponseEntity.accepted().build();
    }

    private UUID userId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "authentication_required", "Authentication required");
        }
    }
}

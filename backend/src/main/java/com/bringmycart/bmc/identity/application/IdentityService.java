package com.bringmycart.bmc.identity.application;

import java.util.UUID;

import com.bringmycart.bmc.identity.api.dto.UserProfileResponse;
import com.bringmycart.bmc.identity.exception.ApiException;
import com.bringmycart.bmc.identity.infrastructure.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private final UserAccountRepository users;

    public IdentityService(UserAccountRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(UUID userId) {
        return users.findById(userId)
            .map(user -> new UserProfileResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name()))
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "authentication_required", "Authentication required"));
    }
}

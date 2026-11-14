package com.bringmycart.bmc.identity.exception;

import org.springframework.http.HttpStatus;

/** Signals an intentional refresh-token rejection after any required revocation is persisted. */
public class RefreshTokenRejectedException extends ApiException {
    public RefreshTokenRejectedException() {
        super(HttpStatus.UNAUTHORIZED, "invalid_refresh_token", "Invalid refresh token");
    }
}

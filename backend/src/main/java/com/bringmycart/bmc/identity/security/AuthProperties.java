package com.bringmycart.bmc.identity.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bmc.auth")
public class AuthProperties {
    private String issuer;
    private String audience;
    private long accessTokenMinutes;
    private long refreshTokenDays;
    private boolean refreshCookieSecure;
    private String allowedCorsOrigins = "";
    private int rateLimitMaxAttempts;
    private long rateLimitWindowMinutes;
    private String privateKeyLocation;
    private String publicKeyLocation;

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public long getAccessTokenMinutes() { return accessTokenMinutes; }
    public void setAccessTokenMinutes(long accessTokenMinutes) { this.accessTokenMinutes = accessTokenMinutes; }
    public long getRefreshTokenDays() { return refreshTokenDays; }
    public void setRefreshTokenDays(long refreshTokenDays) { this.refreshTokenDays = refreshTokenDays; }
    public boolean isRefreshCookieSecure() { return refreshCookieSecure; }
    public void setRefreshCookieSecure(boolean refreshCookieSecure) { this.refreshCookieSecure = refreshCookieSecure; }
    public String getAllowedCorsOrigins() { return allowedCorsOrigins; }
    public void setAllowedCorsOrigins(String allowedCorsOrigins) { this.allowedCorsOrigins = allowedCorsOrigins; }
    public int getRateLimitMaxAttempts() { return rateLimitMaxAttempts; }
    public void setRateLimitMaxAttempts(int rateLimitMaxAttempts) { this.rateLimitMaxAttempts = rateLimitMaxAttempts; }
    public long getRateLimitWindowMinutes() { return rateLimitWindowMinutes; }
    public void setRateLimitWindowMinutes(long rateLimitWindowMinutes) { this.rateLimitWindowMinutes = rateLimitWindowMinutes; }
    public String getPrivateKeyLocation() { return privateKeyLocation; }
    public void setPrivateKeyLocation(String privateKeyLocation) { this.privateKeyLocation = privateKeyLocation; }
    public String getPublicKeyLocation() { return publicKeyLocation; }
    public void setPublicKeyLocation(String publicKeyLocation) { this.publicKeyLocation = publicKeyLocation; }

    public List<String> corsOrigins() {
        return Arrays.stream(allowedCorsOrigins.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
    }
}

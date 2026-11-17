package com.bringmycart.bmc.identity.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.bringmycart.bmc.identity.domain.UserAccount;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;

    public JwtService(JwtEncoder jwtEncoder, AuthProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public IssuedAccessToken issue(UserAccount user) {
        return issue(user, null);
    }

    public IssuedAccessToken issue(UserAccount user, UUID websiteFamilyId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.getAccessTokenMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
            .issuer(properties.getIssuer())
            .subject(user.getId().toString())
            .audience(java.util.List.of(properties.getAudience()))
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claim("role", user.getRole().name());
        if (websiteFamilyId != null) claimsBuilder.claim("web_family", websiteFamilyId.toString());
        JwtClaimsSet claims = claimsBuilder.build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(), claims)).getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }

    public IssuedAccessToken issueExtension(UserAccount user, UUID sessionId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.getExtensionAccessTokenMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(properties.getIssuer())
            .subject(user.getId().toString())
            .audience(java.util.List.of(properties.getAudience()))
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claim("role", user.getRole().name())
            .claim("sid", sessionId.toString())
            .claim("client", "bmc-chrome")
            .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(), claims)).getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }

    public record IssuedAccessToken(String value, Instant expiresAt) { }
}

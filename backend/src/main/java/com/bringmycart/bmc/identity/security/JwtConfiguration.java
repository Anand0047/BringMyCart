package com.bringmycart.bmc.identity.security;

import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.util.StringUtils;

@Configuration
@Profile("!test")
public class JwtConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtEncoder.class)
    JwtEncoder jwtEncoder(AuthProperties properties) {
        requireKeyLocations(properties);
        RSAPublicKey publicKey = readPublicKey(properties.getPublicKeyLocation());
        RSAPrivateKey privateKey = readPrivateKey(properties.getPrivateKeyLocation());
        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("bmc-current").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder jwtDecoder(AuthProperties properties) {
        requireKeyLocations(properties);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(readPublicKey(properties.getPublicKeyLocation())).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(properties.getAudience())
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(properties.getIssuer()), audienceValidator));
        return decoder;
    }

    private void requireKeyLocations(AuthProperties properties) {
        if (!StringUtils.hasText(properties.getPrivateKeyLocation()) || !StringUtils.hasText(properties.getPublicKeyLocation())) {
            throw new IllegalStateException("JWT_PRIVATE_KEY_LOCATION and JWT_PUBLIC_KEY_LOCATION must be configured");
        }
    }

    private RSAPublicKey readPublicKey(String location) {
        try (InputStream input = resource(location).getInputStream()) {
            return RsaKeyConverters.x509().convert(input);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read JWT public key", exception);
        }
    }

    private RSAPrivateKey readPrivateKey(String location) {
        try (InputStream input = resource(location).getInputStream()) {
            return RsaKeyConverters.pkcs8().convert(input);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read JWT private key", exception);
        }
    }

    private Resource resource(String location) {
        return new DefaultResourceLoader().getResource(location);
    }
}

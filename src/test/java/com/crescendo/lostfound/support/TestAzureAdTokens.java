package com.crescendo.lostfound.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mints Azure AD-shaped access tokens (RS256, {@code iss}/{@code aud}/{@code exp}, {@code oid},
 * {@code roles}) signed by a key generated per test run, and provides the matching
 * {@link JwtDecoder} - so end-to-end tests exercise real token validation and claim mapping without
 * an Azure tenant, and no private key is ever committed to the repository.
 */
public final class TestAzureAdTokens {

    /** Must match {@code application-test.yml}. */
    public static final String ISSUER = "https://login.microsoftonline.com/00000000-0000-0000-0000-000000000000/v2.0";
    /** Must match {@code application-test.yml}. */
    public static final String AUDIENCE = "api://lost-found-test";

    private static final RSAKey SIGNING_KEY = generateSigningKey();
    private static final NimbusJwtEncoder ENCODER = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(SIGNING_KEY)));

    private TestAzureAdTokens() {
    }

    /** A token for the user with the given object id ({@code oid}) holding the given App Roles. */
    public static String accessToken(String userId, String... roles) {
        return accessToken(Map.of("oid", userId, "roles", List.of(roles)));
    }

    /** A validly signed, unexpired token for this API carrying exactly the given extra claims. */
    public static String accessToken(Map<String, Object> claims) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claimsSet = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(5)))
                .claims(existing -> existing.putAll(new HashMap<>(claims)));
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(SIGNING_KEY.getKeyID()).build();
        return ENCODER.encode(JwtEncoderParameters.from(header, claimsSet.build())).getTokenValue();
    }

    private static RSAKey generateSigningKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-signing-key").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not generate test signing key", e);
        }
    }

    /** Replaces the Azure-backed decoder with one trusting the test signing key, still checking issuer and audience. */
    @TestConfiguration(proxyBeanMethods = false)
    public static class DecoderConfiguration {

        @Bean
        JwtDecoder jwtDecoder() throws JOSEException {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                    new JwtIssuerValidator(ISSUER),
                    new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(AUDIENCE))));
            return decoder;
        }
    }
}

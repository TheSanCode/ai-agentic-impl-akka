package io.agenticawithakka.security;

import io.agenticawithakka.domain.contracts.ContractValidation;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Creates a signature-verifying JWT decoder with explicit issuer, audience and time validation. */
public final class OidcJwtDecoderFactory {
    private OidcJwtDecoderFactory() {}

    public static JwtDecoder create(String issuerUri, String jwkSetUri, String audience) {
        ContractValidation.text(issuerUri, "issuerUri", 512);
        ContractValidation.text(jwkSetUri, "jwkSetUri", 2048);
        ContractValidation.matches(audience, "audience", ContractValidation.REFERENCE);
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> issuerAndTime = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> expectedAudience = jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new org.springframework.security.oauth2.core.OAuth2Error(
                                "invalid_token", "Required audience is missing", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerAndTime, expectedAudience));
        return decoder;
    }
}

package io.agenticawithakka.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.proc.SecurityContext;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;

class OidcJwtDecoderTest {
    private static final String ISSUER = "http://127.0.0.1/realms/lab";
    private static final String AUDIENCE = "agenticawithakka-api";
    private RSAKey signingKey;
    private RSAKey wrongSigningKey;
    private HttpServer jwksServer;
    private org.springframework.security.oauth2.jwt.JwtDecoder decoder;

    @BeforeEach
    void startJwksServer() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        wrongSigningKey = new RSAKeyGenerator(2048).keyID("wrong-key").generate();
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/jwks", exchange -> {
            byte[] body = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        jwksServer.start();
        String jwksUri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks";
        decoder = OidcJwtDecoderFactory.create(ISSUER, jwksUri, AUDIENCE);
    }

    @AfterEach
    void stopJwksServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @Test
    void acceptsCorrectlySignedUnexpiredTokenWithExpectedIssuerAndAudience() {
        Jwt decoded = decoder.decode(token(signingKey, ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(60)));
        assertThat(decoded.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(decoded.getSubject()).isEqualTo("alice");
        assertThat(decoded.getAudience()).containsExactly(AUDIENCE);
    }

    @Test
    void rejectsWrongIssuer() {
        assertInvalid(token(signingKey, "http://other-issuer/realms/lab", List.of(AUDIENCE),
                Instant.now().plusSeconds(60)));
    }

    @Test
    void rejectsMissingExpectedAudience() {
        assertInvalid(token(signingKey, ISSUER, List.of("another-api"), Instant.now().plusSeconds(60)));
    }

    @Test
    void rejectsExpiredToken() {
        assertInvalid(token(signingKey, ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(300)));
    }

    @Test
    void rejectsInvalidSignature() {
        assertInvalid(token(wrongSigningKey, ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(60)));
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private static String token(RSAKey key, String issuer, List<String> audiences, Instant expiresAt) {
        var jwkSource = new com.nimbusds.jose.jwk.source.ImmutableJWKSet<SecurityContext>(
                new JWKSet(key));
        var encoder = new NimbusJwtEncoder(jwkSource);
        Instant now = Instant.now();
        Instant issuedAt = expiresAt.isBefore(now) ? expiresAt.minusSeconds(120) : now.minusSeconds(1);
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("alice")
                .audience(audiences)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}

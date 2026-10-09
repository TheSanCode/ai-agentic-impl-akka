package io.agenticawithakka.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.proc.SecurityContext;
import com.sun.net.httpserver.HttpServer;
import io.agenticawithakka.domain.contracts.ProjectId;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OidcResourceServerIntegrationTest.IdentityProbe.class)
class OidcResourceServerIntegrationTest {
    private static final String ISSUER = "http://127.0.0.1/realms/integration";
    private static final String AUDIENCE = "agenticawithakka-api";
    private static RSAKey signingKey;
    private static RSAKey wrongSigningKey;
    private static HttpServer jwksServer;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void oidcProperties(DynamicPropertyRegistry registry) throws Exception {
        startJwksServer();
        registry.add("app.security.issuer-uri", () -> ISSUER);
        registry.add("app.security.jwk-set-uri", OidcResourceServerIntegrationTest::jwksUri);
        registry.add("app.security.audience", () -> AUDIENCE);
        registry.add("app.security.principals[0].issuer", () -> ISSUER);
        registry.add("app.security.principals[0].subject", () -> "alice");
        registry.add("app.security.principals[0].projects.alpha.resources[0]", () -> "service:billing");
        registry.add("app.security.principals[0].projects.alpha.sources[0]", () -> "mock-logs");
    }

    @AfterAll
    static void stopJwksServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @Test
    void verifiedBearerTokenMapsOnlyConfiguredProjectMembership() throws Exception {
        var response = send(token(signingKey));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"alpha\":true", "\"beta\":false");
    }

    @Test
    void invalidSignatureIsRejectedByTheResourceServerBeforeIdentityMapping() throws Exception {
        var response = send(token(wrongSigningKey));

        assertThat(response.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> send(String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/test/identity"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private static String token(RSAKey key) {
        var jwkSource = new com.nimbusds.jose.jwk.source.ImmutableJWKSet<SecurityContext>(
                new JWKSet(key));
        var encoder = new NimbusJwtEncoder(jwkSource);
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject("alice")
                .audience(List.of(AUDIENCE))
                .issuedAt(now.minusSeconds(1))
                .expiresAt(now.plusSeconds(60))
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static synchronized void startJwksServer() throws Exception {
        if (jwksServer != null) {
            return;
        }
        signingKey = new RSAKeyGenerator(2048).keyID("integration-key").generate();
        wrongSigningKey = new RSAKeyGenerator(2048).keyID("invalid-key").generate();
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
    }

    private static String jwksUri() {
        return "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks";
    }

    @RestController
    static class IdentityProbe {
        private final AuthenticatedIdentityContextResolver identities;

        IdentityProbe(AuthenticatedIdentityContextResolver identities) {
            this.identities = identities;
        }

        @GetMapping("/api/test/identity")
        IdentityResponse identity(Authentication authentication) {
            var reference = identities.resolve(authentication);
            return new IdentityResponse(
                    identities.mayAccessProject(reference, new ProjectId("alpha")),
                    identities.mayAccessProject(reference, new ProjectId("beta")));
        }
    }

    private record IdentityResponse(boolean alpha, boolean beta) {
    }
}

package io.agenticawithakka.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agenticawithakka.application.ports.PolicyAction;
import io.agenticawithakka.application.ports.PolicyRequest;
import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.config.ServiceProperties;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.ProjectId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedIdentityContextResolverTest {
    private static final String ISSUER = "https://identity.example.test/realms/lab";
    private static final String SUBJECT = "alice";
    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private static final ProjectId ALPHA = new ProjectId("alpha");
    private static final ProjectId BETA = new ProjectId("beta");

    @Test
    void mapsVerifiedIssuerAndSubjectToOnlyServerConfiguredProjectAndSourceGrants() {
        var store = new MockIdentityContextStore(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(15));
        var resolver = new AuthenticatedIdentityContextResolver(store, accessResolver(), Clock.fixed(NOW, ZoneOffset.UTC));
        var identity = resolver.resolve(authentication(ISSUER, SUBJECT));

        assertThat(resolver.mayAccessProject(identity, ALPHA)).isTrue();
        assertThat(resolver.mayAccessProject(identity, BETA)).isFalse();
        var context = store.resolve(identity).orElseThrow();
        assertThat(context.subject()).isEqualTo(ISSUER + "\n" + SUBJECT);
        assertThat(context.expiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(context.mayAccessResource(ALPHA, "service:billing")).isTrue();
        assertThat(context.mayAccessSource(ALPHA, "mock-logs")).isTrue();
        assertThat(context.mayAccessSource(ALPHA, "mock-admin")).isFalse();

        var policy = new MockProjectPolicyService(store);
        assertThat(policy.decide(new PolicyRequest(identity, ALPHA, PolicyAction.START_EXECUTION, "execution:1"))
                        .toCompletableFuture()
                        .join()
                        .permitted())
                .isTrue();
        assertThat(policy.decide(new PolicyRequest(identity, BETA, PolicyAction.START_EXECUTION, "execution:2"))
                        .toCompletableFuture()
                        .join()
                        .reasonCode())
                .isEqualTo("project.notMember");
    }

    @Test
    void issuerAndSubjectMustBothMatchAnActiveServerSideMapping() {
        var store = new MockIdentityContextStore(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(15));
        var resolver = new AuthenticatedIdentityContextResolver(store, accessResolver(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertDenied(resolver, authentication("https://untrusted.example.test", SUBJECT));
        assertDenied(resolver, authentication(ISSUER, "unknown-user"));
        assertThatThrownBy(() -> resolver.resolve(
                        new UsernamePasswordAuthenticationToken("alice", "password")))
                .isInstanceOf(PortException.class)
                .extracting(error -> ((PortException) error).errorCode())
                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    private static void assertDenied(
            AuthenticatedIdentityContextResolver resolver, JwtAuthenticationToken authentication) {
        assertThatThrownBy(() -> resolver.resolve(authentication))
                .isInstanceOf(PortException.class)
                .extracting(error -> ((PortException) error).errorCode())
                .isEqualTo(ErrorCode.DENIED);
    }

    private static JwtAuthenticationToken authentication(String issuer, String subject) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject(subject)
                .audience(List.of("agenticawithakka-api"))
                .issuedAt(NOW.minusSeconds(1))
                .expiresAt(NOW.plusSeconds(60))
                .build();
        var authentication = new JwtAuthenticationToken(jwt);
        authentication.setAuthenticated(true);
        return authentication;
    }

    private static ConfiguredProjectAccessResolver accessResolver() {
        var security = new ServiceProperties.Security(
                "https://identity.example.test/realms/lab",
                "https://identity.example.test/realms/lab/protocol/openid-connect/certs",
                "agenticawithakka-api",
                List.of(new ServiceProperties.PrincipalAccess(
                        ISSUER,
                        SUBJECT,
                        Map.of(
                                "alpha",
                                new ServiceProperties.ProjectAccess(
                                        Set.of("service:billing"), Set.of("mock-logs"))))));
        return new ConfiguredProjectAccessResolver(security);
    }
}

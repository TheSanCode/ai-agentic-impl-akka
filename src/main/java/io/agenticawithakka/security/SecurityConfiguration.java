package io.agenticawithakka.security;

import io.agenticawithakka.config.ServiceProperties;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Stateless bearer-token API security. Health remains public; unconfigured business routes deny all. */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ServiceProperties properties, JwtDecoder decoder)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint()))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/api/health").permitAll();
                    if (properties.security().configured()) {
                        authorize.anyRequest().authenticated();
                    } else {
                        authorize.anyRequest().denyAll();
                    }
                })
                .oauth2ResourceServer(resourceServer ->
                        resourceServer.jwt(jwt -> jwt.decoder(decoder)));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(ServiceProperties properties) {
        var security = properties.security();
        if (!security.configured()) {
            if (security.partiallyConfigured()) {
                throw new IllegalStateException(
                        "app.security.issuer-uri, jwk-set-uri and audience must be configured together");
            }
            return token -> {
                throw new JwtException("OIDC resource-server validation is not configured");
            };
        }
        return OidcJwtDecoderFactory.create(security.issuerUri(), security.jwkSetUri(), security.audience());
    }

    @Bean
    ProjectAccessResolver projectAccessResolver(ServiceProperties properties) {
        return new ConfiguredProjectAccessResolver(properties.security());
    }

    @Bean
    MockIdentityContextStore mockIdentityContextStore(Clock clock) {
        return new MockIdentityContextStore(clock, Duration.ofMinutes(15));
    }

    @Bean
    AuthenticatedIdentityContextResolver authenticatedIdentityContextResolver(
            MockIdentityContextStore contexts, ProjectAccessResolver accessResolver, Clock clock) {
        return new AuthenticatedIdentityContextResolver(contexts, accessResolver, clock);
    }

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}

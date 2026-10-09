# OIDC/JWT resource-server authentication and identity mapping

Date: 9 October 2026. Scope: P1-04 Spring Security bearer-token verification and server-side project grant lookup.

## Decision and behavior

- The API uses Spring Security's stateless OAuth2 resource-server support. Configure `app.security.issuer-uri`, `app.security.jwk-set-uri` and `app.security.audience` together. If none are configured, `/api/health` remains public and every other route is denied; a partial configuration fails application startup.
- `OidcJwtDecoderFactory` uses Nimbus JWK-set signature verification and Spring's default issuer/time validators, plus an exact expected-audience check. Tokens without valid signatures, the configured issuer, the required audience or valid time claims are rejected by the resource server.
- After Spring Security has authenticated the JWT, `AuthenticatedIdentityContextResolver` looks up grants by the exact validated `(iss, sub)` pair. Project IDs supplied by a request do not grant membership. Missing mappings and empty memberships are denied. Resource and source grants are separate.
- The resolver creates an opaque server-held identity-context reference. Its expiry is no later than either the local store lifetime or the verified JWT expiry. This mapping remains in-memory for the local lab.
- The tool registry checks tool/resource policy before every invocation and rechecks evidence sources before returning passages. The mock source also checks that the identity context, current project membership and source grant are still active when it consumes a delegated credential.
- `TaskResumeAuthorizer` rechecks the original subject and current project membership before resuming a server-held task envelope. `StoredTaskResultAuthorizer` rechecks task ownership, membership, project binding and each cited evidence source before returning a stored result. Membership revocation removes the project grant from the in-memory context immediately.
- Server-side grants are configured under `app.security.principals`. Example configuration shape:

```yaml
app:
  security:
    issuer-uri: https://identity.example.test/realms/lab
    jwk-set-uri: https://identity.example.test/realms/lab/protocol/openid-connect/certs
    audience: agenticawithakka-api
    principals:
      - issuer: https://identity.example.test/realms/lab
        subject: alice
        projects:
          alpha:
            resources: [service:billing]
            sources: [mock-logs]
```

This example documents property shape only; it does not identify a selected production identity provider or authorize a deployment. Membership resolution errors must not be replaced with client-supplied project claims or token claims.

## Verification

On Temurin 25, `.\mvnw.cmd -B -ntp clean verify` passed: **137 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.

| Test | Coverage |
| --- | --- |
| `OidcJwtDecoderTest` | RSA signature verification with a local JWK endpoint; wrong issuer, wrong audience, expired token and invalid signature are rejected. |
| `AuthenticatedIdentityContextResolverTest` | Exact issuer+subject mapping, missing mapping denial, project binding, distinct resource/source grants, authenticated-principal requirement and context expiry bounded by JWT expiry. |
| `OidcResourceServerIntegrationTest` | Configured Spring Security HTTP bearer flow accepts a signed token, creates the server-side identity context and grants only the configured project; a signature-invalid token receives HTTP 401 before mapping. |
| `HealthEndpointTest` | Health stays public; unconfigured business and management routes are denied with HTTP 401. |
| `AuthorizationRevalidationTest`, `MockAdaptersIntegrationTest`, `MockDelegatedSourceTest` and `MockIdentityPolicyTest` | Resume, every tool invocation, source credential use and completed-result/citation access are denied after project-membership revocation; another subject and cross-project evidence are rejected. |

The tests use generated RSA keys and a loopback-only JWK fixture; they require no external identity provider or credentials.

## Limits and remaining decisions

- No production IdP, browser login, interactive consent, membership database, provisioning workflow, persistent identity context, audit store or multi-instance consistency is selected or implemented. The resume/result authorization gates are adapter services, not endpoints or a durable background-task/result store; callers must load task envelopes from trusted server-side storage and invoke the gates.
- Revocation is in-process and affects identity contexts in this adapter instance. There is no distributed revocation propagation or persistence, and expiration of the original in-memory context cannot support cross-process subject rebinding for a resumed task.
- The HTTP integration test exercises one correctly signed principal and signature rejection. Issuer, audience and expiry negative cases are exercised directly against the production decoder factory.
- Every later business boundary must recheck current project/source authorization. Real-source OBO, token exchange, consent and revocation are still not implemented.
- OIDC does not resolve Akka's production license/runtime-key decision, which remains separate and open.

# Local identity, policy and mock delegated-source adapters (P1-04)

Date: 9 October 2026. Branch `feature/agentic`. Scope: in-memory identity contexts, project/resource/source policy and read-only synthetic source delegation. This is a local-lab simulation, not an OIDC implementation or real-source OBO proof.

## Decision

- **Identity context store.** `MockIdentityContextStore` issues cryptographically random, opaque references only when called with an already authenticated subject. It holds project membership, explicitly granted resource IDs and source IDs server-side; contexts expire within a configured bounded lifetime and can be revoked. Unknown, expired and revoked references cannot be resolved. The store does not authenticate a subject or accept a client/model-supplied identity as proof.
- **Project policy.** `MockProjectPolicyService` fails closed for missing identity and non-member project access. Project membership permits the bounded execution/tool actions, but `READ_EVIDENCE` requires an explicit resource grant for a tool resource argument or an explicit source grant for returned evidence. Project/application administrator status does not grant source access.
- **Mock delegated credentials.** `MockDelegatedTokenProvider` requires a current identity context, project membership, source grant and configured audience/scope subset. It issues short-lived HMAC-SHA256 credentials bound to the identity-context reference, subject, project, audience, scopes and expiry. No shared credential fallback exists.
- **Independent mock source checks.** `MockSourceConnector` verifies the credential signature and metadata, trusted identity/project binding, exact source audience, required operation scope, deadline, expiry and its own subject/project ACL. It returns only fixture passages for the requested project and applies the caller's record limit.
- **Composition boundary.** The implementations are framework-free adapters and are exercised through the actual `ToolRegistry`; no Spring bean configuration, API authentication endpoint, local identity-provider service or synthetic data bootstrap was added.

## Verification

On Temurin 25, `.\mvnw.cmd -B -ntp clean verify` passed: **122 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Eight tests were added:

| Test class | Count | Coverage |
| --- | ---: | --- |
| `MockIdentityPolicyTest` | 2 | Opaque context creation, expiry/revocation, project membership, specific resource/source grants and separation of project membership from evidence access. |
| `MockDelegatedSourceTest` | 4 | Missing identity/credential, unauthorized project, wrong audience/scope, tampered signature, expiry and independent source ACL denial. |
| `MockAdaptersIntegrationTest` | 2 | Authorized tool invocation through policy, delegated provider and source; project-resource and source-grant denials through the same tool gate. |

No dependency was added. The packaged-JAR health smoke check was not rerun because HTTP/configuration was unchanged; the full application test suite, including health endpoint tests, passed.

## Not established / remaining work

- The mock identity store assumes its caller has already authenticated the principal. Spring Security OIDC/JWT verification, issuer/audience/signature validation, Keycloak configuration, browser login and API composition are not implemented.
- HMAC credentials are a synthetic local protocol only. They do not prove Keycloak token exchange or support for any enterprise source. Real connectors still need their own delegated authorization, consent, expiry and revocation tests. P1-04 therefore remains partial.
- The adapters use in-memory grants and fixtures; they do not establish persistent membership, policy administration, audit, revocation propagation or multi-instance consistency.
- Akka's production license decision remains separate and open. This work neither changes nor implies approval of the development/non-production-only policy recorded in [decision 0002](0002-integration-probes.md).

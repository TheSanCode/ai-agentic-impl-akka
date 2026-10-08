# Spring AI and Akka integration probes

Date: 7 October 2026. Follows scaffold commit `d10a02d` on `feature/agentic`. Scope: dependency resolution and minimal runtime probes, not agent workflows or production integration.

## Evidence and decisions

| Probe | Resolution | Java 25 runtime result | Decision |
| --- | --- | --- | --- |
| Spring AI 2.0.1 Ollama starter with Boot 4.0.8 | Passed using Maven Central | 1 test passed on Temurin 25.0.4.1+1; actual Boot auto-configuration, ChatClient, Ollama HTTP adapter and JSON round trip against a loopback fixture | This narrow combination is verified. Keep application integration pending contracts and real-model acceptance. |
| Akka Typed 2.10.23 (`_2.13`) with Boot 4.0.8 dependency management | Failed: artifact absent from configured Maven Central | Test could not compile/run; Java 25 compatibility remains vendor-certified only | Blocked on authorized Akka repository access. Do not substitute an older public release or claim the test passed. |

The independent POMs and test sources live in [compatibility](../../compatibility/README.md). The root application POM and source are unchanged. These are not combined Spring AI/Akka tests and do not demonstrate agent communication, cancellation, persistence or recovery.

Spring AI build completed at 23:15:48 America/Toronto; dependency tree completed at 23:16:13. Resolved versions inspected: Boot starters 4.0.8, Spring AI modules 2.0.1, Spring Framework 7.0.9, Jackson databind/core 3.1.5, Reactor Core 3.8.7, JUnit 6.0.3. The Boot parent manages shared dependencies; no Boot 4.1 starter was selected. Surefire: 1 test, 0 failures, 0 errors, 0 skipped. The test asserts Java feature version 25, a configured synthetic model ID, non-streaming request content and the parsed fixture response. No real inference was attempted.

Akka build failed at 23:16:34 America/Toronto with:

```text
Could not find artifact com.typesafe.akka:akka-actor-typed_2.13:jar:2.10.23 in central (https://repo.maven.apache.org/maven2)
```

No user Maven settings file or `AKKA_REPOSITORY_URL` / `AKKA_LICENSE_KEY` environment value was present in the checked execution environment. Only presence booleans were printed, not credential values. No authorized tokenized endpoint was tested. The missing Central artifact is an access/configuration blocker, not proof of Java incompatibility or proof that the vendor repository is unavailable.

## Repository and licensing requirements

- Spring AI release artifacts are available from Maven Central without a vendor account or extra repository. The selected source release is Apache-2.0. Provider services and model weights carry separate terms; none was used in this fixture. [Repository/Boot guidance](https://docs.spring.io/spring-ai/reference/getting-started.html), [2.0.1 license](https://github.com/spring-projects/spring-ai/blob/v2.0.1/LICENSE.txt).
- Akka's current release listing identifies core 2.10.23 and a secure repository requiring a tokenized URL obtained through an authorized account. Configure that privately, outside source control; do not use credentials from unrelated projects. [Official release and repository instructions](https://doc.akka.io/libraries/akka-dependencies/current/).
- Akka certifies Temurin 25, but that vendor statement does not replace resolution and runtime tests for this dependency graph. [Certified Java versions](https://doc.akka.io/libraries/akka-dependencies/current/java-versions.html).
- Akka is source available under BSL-1.1. Its FAQ permits development/non-production testing and describes separate production licensing and additional-use terms. Project eligibility and production approval remain unresolved; no license purchase or account enrollment occurred. [Vendor licensing FAQ](https://akka.io/bsl-license-faq), [license details](https://doc.akka.io/libraries/akka-core/current/project/licenses.html).
- Akka documents production configuration through `akka.license-key`; local development can start without a key but the ActorSystem terminates after a period. Repository tokens and runtime license keys are different concerns. Key provisioning, validity and long-running use remain to verify; this short probe does not bypass the check. [Runtime license configuration](https://doc.akka.io/libraries/akka-core/current/general/configuration.html#license-key).

## Remaining gates

1. Provide authorized Akka repository access locally; rerun the isolated test and inspect its resolved graph. Its current source has not compiled, so API corrections may still be needed.
2. Resolve Akka licensing/eligibility and runtime key policy before sustained lab or production use. Do not infer permission from artifact availability.
3. Test both integrations together before asserting full-stack compatibility; then define replaceable ports before workflows.
4. Select and license actual model weights, measure hardware fit, and test actual Ollama chat/structured output, streaming and embeddings separately.

The scaffold's 9 passing tests and packaged health checks were not repeated. The normal shell still fails before process creation with `helper_unknown_error: setup refresh had errors`; approved outside-sandbox commands supplied these probe results. That execution-environment issue remains open. Nothing was deployed.

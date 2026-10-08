# Spring AI and Akka integration probes

Date: 7 October 2026. Follows scaffold commit `d10a02d` on `feature/agentic`. Scope: dependency resolution and minimal runtime probes, not agent workflows or production integration.

## Evidence and decisions

| Probe | Resolution | Java 25 runtime result | Decision |
| --- | --- | --- | --- |
| Spring AI 2.0.1 Ollama starter with Boot 4.0.8 | Passed using Maven Central | 1 test passed on Temurin 25.0.4.1+1; actual Boot auto-configuration, ChatClient, Ollama HTTP adapter and JSON round trip against a loopback fixture | This narrow combination is verified. Keep application integration pending contracts and real-model acceptance. |
| Akka Typed 2.10.23 (`_2.13`) with Boot 4.0.8 dependency management | Resolved from the local Maven cache; a fresh authorized repository resolution is not verified | 1 test passed on Temurin 25.0.4.1+1; Spring-managed typed ActorSystem, bounded ask/reply, and termination | This narrow runtime combination passed. Authorized repository access and profile activation remain unverified; licensing and production runtime-key decisions remain open. |

The independent POMs and test sources live in [compatibility](../../compatibility/README.md). The root application POM and source are unchanged. These are not combined Spring AI/Akka tests and do not demonstrate agent communication, cancellation, persistence or recovery.

Spring AI build completed at 23:15:48 America/Toronto; dependency tree completed at 23:16:13. Resolved versions inspected: Boot starters 4.0.8, Spring AI modules 2.0.1, Spring Framework 7.0.9, Jackson databind/core 3.1.5, Reactor Core 3.8.7, JUnit 6.0.3. The Boot parent manages shared dependencies; no Boot 4.1 starter was selected. Surefire: 1 test, 0 failures, 0 errors, 0 skipped. The test asserts Java feature version 25, a configured synthetic model ID, non-streaming request content and the parsed fixture response. No real inference was attempted.

The initial Akka build failed at 23:16:34 America/Toronto with:

```text
Could not find artifact com.typesafe.akka:akka-actor-typed_2.13:jar:2.10.23 in central (https://repo.maven.apache.org/maven2)
```

No user Maven settings file or `AKKA_REPOSITORY_URL` / `AKKA_LICENSE_KEY` environment value was present in the initial execution environment. Only presence booleans were printed, not credential values. The missing Central artifact is an access/configuration blocker, not proof of Java incompatibility or proof that the vendor repository is unavailable.

After the user indicated repository access was configured, the isolated Akka probe was retried on Temurin 25 both normally and with `-U` to bypass Maven's cached not-found result. Both attempts still resolved only against Maven Central and failed before compilation; no Akka test ran. A post-retry presence check still found no `$HOME\.m2\settings.xml`, `AKKA_REPOSITORY_URL`, or `AKKA_LICENSE_KEY` in this execution environment. The user may have configured access outside the environment visible to this Maven process; active vendor-repository access is not established. No credential values or settings contents were inspected.

On the next retry the user confirmed configuration and the default Maven settings file existed, but `mvn -U ... verify` still failed before compilation and named only Maven Central. The probe POM declares no `<repositories>` entry, and the previously supplied settings fragment contained only `<servers>`, not an active profile with a repository URL. Thus Maven still does not see the authorized Akka repository. Settings contents and credentials were not inspected or printed.

The user then confirmed an active profile. Maven Help Plugin `help:active-profiles` nevertheless reported no active profiles for the probe project; an explicit retry with `-P akka-repository` warned that this profile does not exist and again resolved only against Central. No settings data was read. The active profile ID and repository declaration need to be verified locally; the Akka compatibility test remains uncompiled/unrun.

The non-secret profile ID was provided as `akka-repository`. Passing the settings file explicitly and `-P akka-repository` activated the external profile in one Maven Help Plugin check. A subsequent build still reported the profile missing/inactive and initially failed at repository resolution; the URL is not reproduced. Therefore, the later successful test below must not be taken as proof that the vendor repository was contacted successfully.

On 8 October 2026, following the user's compiler failure report, the Akka probe was rerun with Temurin 25 explicitly selected (`JAVA_HOME` and PATH), the local settings file, `-P akka-repository`, and `-U`. Maven warned that the requested profile could not be activated, but used Akka 2.10.23 artifacts present in the local Maven cache; `AkkaCompatibilityTest` passed (1 test, 0 failures/errors/skips). It created a Spring-managed typed ActorSystem, performed bounded asynchronous ask/reply, and verified termination. `dependency:tree` confirmed `akka-actor-typed_2.13`, `akka-actor_2.13`, and `akka-slf4j_2.13` at 2.10.23. This establishes a limited Java 25 runtime result using locally available artifacts, not a clean-cache or authorized remote-repository resolution.

After the user reported the same `release version 25 not supported` error again, the command in [compatibility instructions](../../compatibility/README.md) was rerun with its Java-version guard. Maven reported Java 25.0.4.1 / Eclipse Adoptium and the Akka test again passed (1 test, 0 failures/errors/skips). The inactive-profile warning remained; dependencies were locally cached. This confirms the compiler failure is avoided when the environment setup and wrapper run in the same PowerShell session, but does not resolve repository access.

## Probe invocation hardening

To prevent repeat JDK/profile selection mistakes, `scripts/Test-AkkaCompatibility.ps1` now selects a detected JDK 25, verifies the Maven JVM before compilation, and activates the repository profile defined by the isolated Akka POM. It isolates the Maven invocation with a temporary empty settings file, avoiding the user's stale/misnamed external profile. The authorized tokenized HTTPS URL is supplied locally through a secure PowerShell prompt or `AKKA_REPOSITORY_URL`; it is not committed, passed as a command argument or printed. Maven output URLs are redacted. The script removes its temporary settings file on exit.

Validated with `-ValidateOnly` on 8 October 2026: Maven reported Temurin 25.0.4.1+1, and the Akka POM's `akka-repository` profile was active with no warning; this mode does not require repository access or run tests. The Akka runtime test has previously passed from the local cache, but this invocation hardening has not yet been used for a fresh vendor-repository download because the authorized URL is not available to this process. Do not treat `-ValidateOnly` as a dependency or integration test.

On 8 October 2026 the user ran the full hardened script locally and pasted its redacted output: Temurin 25.0.4.1, `akka-repository` profile active with no warning, `-U verify` → `AkkaCompatibilityTest` 1 test, 0 failures/errors/skips, `BUILD SUCCESS`; ActorSystem logged the expected `Dev use only` license warning. Because `-ntp` suppresses transfer logs and Akka 2.10.23 was already in the local cache, this run does not prove the artifacts were fetched from the vendor repository; a cache-isolated rerun (moved `com\typesafe\akka` cache or `-Dmaven.repo.local=<empty temp dir>`) is still needed for that.

## Repository and licensing requirements

- Spring AI release artifacts are available from Maven Central without a vendor account or extra repository. The selected source release is Apache-2.0. Provider services and model weights carry separate terms; none was used in this fixture. [Repository/Boot guidance](https://docs.spring.io/spring-ai/reference/getting-started.html), [2.0.1 license](https://github.com/spring-projects/spring-ai/blob/v2.0.1/LICENSE.txt).
- Akka's current release listing identifies core 2.10.23 and a secure repository requiring a tokenized URL obtained through an authorized account. Configure that privately, outside source control; do not use credentials from unrelated projects. [Official release and repository instructions](https://doc.akka.io/libraries/akka-dependencies/current/).
- Akka certifies Temurin 25, but that vendor statement does not replace resolution and runtime tests for this dependency graph. [Certified Java versions](https://doc.akka.io/libraries/akka-dependencies/current/java-versions.html).
- Akka is source available under BSL-1.1. Its FAQ permits development/non-production testing and describes separate production licensing and additional-use terms. Project eligibility and production approval remain unresolved; no license purchase or account enrollment occurred. [Vendor licensing FAQ](https://akka.io/bsl-license-faq), [license details](https://doc.akka.io/libraries/akka-core/current/project/licenses.html).
- Akka documents production configuration through `akka.license-key`; local development can start without a key but the ActorSystem terminates after a period. Repository tokens and runtime license keys are different concerns. Key provisioning, validity and long-running use remain to verify; this short probe does not bypass the check. [Runtime license configuration](https://doc.akka.io/libraries/akka-core/current/general/configuration.html#license-key).

## Remaining gates

1. Prove authorized Akka repository resolution with a cache-isolated rerun of `scripts/Test-AkkaCompatibility.ps1` and inspect its resolved graph. The isolated test source compiles and passes on Java 25 with locally available artifacts.
2. Resolve Akka licensing/eligibility and runtime key policy before sustained lab or production use. Do not infer permission from artifact availability.
3. Test both integrations together before asserting full-stack compatibility; then define replaceable ports before workflows.
4. Select and license actual model weights, measure hardware fit, and test actual Ollama chat/structured output, streaming and embeddings separately.

The scaffold's 9 passing tests and packaged health checks were not repeated. The normal shell still fails before process creation with `helper_unknown_error: setup refresh had errors`; approved outside-sandbox commands supplied these probe results. That execution-environment issue remains open. Nothing was deployed.

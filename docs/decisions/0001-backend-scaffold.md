# Backend scaffold dependency decision

Date: 7 October 2026. Scope: P1-01 dependency investigation and P1-02 backend scaffold only.

Follow-up: [isolated integration probes](0002-integration-probes.md) now verify a limited Spring AI/Ollama fixture on Java 25; Akka resolution remains blocked. The table below records the original scaffold decision, not the later probe outcome.

## Versions and evidence

| Component | Exact version | Decision / verification |
| --- | --- | --- |
| Java | Eclipse Temurin 25.0.4.1+1 | Installed Java/Javac version verified. Compile for Java 25; no preview features. Default machine PATH still selects Microsoft Java 21.0.8. |
| Spring Boot | 4.0.8 | Official 4.0 requirements support Java 17–26 and Maven 3.6.3+. Wrapper build, 9 tests and packaged-JAR HTTP check passed on Temurin 25.0.4.1+1. This verifies the scaffold combination only. |
| Spring AI | 2.0.1 | Published BOM confirmed in Maven Central metadata; official compatibility covers Boot 4.0.x/4.1.x. Candidate only, not a runtime dependency: there is no model integration in this scaffold. Combined runtime compatibility remains untested. |
| Akka Typed | 2.10.23, Scala artifact suffix 2.13 | Official current core release; Temurin 25 is certified. Candidate only. Tokenized repository access and license review are unresolved; no artifact resolution or combined runtime test claimed. |
| Maven | 3.9.11 | Pinned wrapper distribution; version and successful build verified with Temurin 25. |
| Maven Wrapper | 3.3.4 | Official only-script wrapper generated and Windows script tested; no wrapper JAR required. |

The scaffold adds only Spring MVC and Bean Validation plus test support. Boot manages their transitive versions. Spring AI and Akka are deliberately not imported into the application dependency graph before there is a concrete implementation need. No alternative agent runtime is implemented. A successful scaffold build cannot establish four-way Java/Boot/AI/Akka compatibility.

## Initial license inventory

| Component | License / outstanding review |
| --- | --- |
| Temurin / OpenJDK | GPLv2 with Classpath Exception |
| Spring Boot, Spring Framework, Spring AI | Apache-2.0 |
| Maven and Maven Wrapper | Apache-2.0 |
| Embedded Tomcat | Apache-2.0 |
| Hibernate Validator | Apache-2.0 |
| Jakarta Validation API | Apache-2.0 |
| Jackson | Apache-2.0 |
| SLF4J / Logback | MIT / EPL-1.0 or LGPL-2.1 |
| JUnit / AssertJ / Mockito (test only) | EPL-2.0 / Apache-2.0 / MIT |
| Akka | BSL-1.1, source available; production terms require explicit review. No acceptance of terms or license purchase made. |

This is the scaffold component inventory, not a full transitive legal review, SBOM or production approval. Boot's test starter chooses its supported JUnit version; the design's JUnit 5 proposal does not override the tested Boot dependency set.

## Open decisions and boundaries

- Akka repository credentials, license approval, dependency resolution and adapter tests remain open. Do not put tokenized URLs in source or logs.
- Spring AI model/provider modules and their combined Boot tests belong to later work. No model, container or image is selected or downloaded here.
- Java 25 is the LTS baseline, not a latest-GA Java experiment.
- Namespace `io.agenticawithakka` is a provisional local project namespace, not a claimed public domain.
- Health is a public local process check only. Bind to loopback by default. Identity/project authorization must precede any business API.
- WSL2/Podman, models, identity, database, approved images and Phase 0 decisions remain pending; none is required to compile this scaffold.
- Older design documents refer to stale requirements versions and differ on when Akka is introduced. Current requirements and Phase 1 instructions govern future work; this scaffold does not resolve those agent-delivery choices.

## Primary references

- [Boot 4.0 system requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html)
- [Spring AI getting started and compatibility](https://docs.spring.io/spring-ai/reference/getting-started.html)
- [Boot Maven metadata](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml)
- [Spring AI Maven metadata](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-bom/maven-metadata.xml)
- [Akka library versions and repository requirements](https://doc.akka.io/libraries/akka-dependencies/current/)
- [Akka certified Java versions](https://doc.akka.io/libraries/akka-dependencies/current/java-versions.html)
- [Akka licensing](https://akka.io/bsl-license-faq)

Build and runtime evidence is recorded in the [Windows backend guide](../implementation/backend-scaffold.md). P1-01 remains partially complete until the outstanding runtime and license decisions are resolved. Artifact publication establishes availability only, not compatibility.

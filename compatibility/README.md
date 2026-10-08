# Isolated dependency probes

These standalone Maven test projects use the scaffold's Boot 4.0.8 / Java 25 baseline. They are not modules or dependencies of the application. No agents, workflows, real models or enterprise sources are implemented.

From the repository root in PowerShell:

```powershell
$env:JAVA_HOME = Join-Path $env:LOCALAPPDATA 'Programs/Eclipse Adoptium/jdk-25.0.4.1+1'
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
.\mvnw.cmd -B -ntp -f compatibility/spring-ai/pom.xml verify
.\mvnw.cmd -B -ntp -f compatibility/spring-ai/pom.xml dependency:tree '-DoutputFile=target/dependency-tree.txt'
.\mvnw.cmd -B -ntp -f compatibility/akka/pom.xml verify
```

Verified 7 October 2026: Spring AI command passed **1 test** and dependency-tree generation passed. The Akka command **failed dependency resolution before compilation/tests** because the configured Maven Central repository does not contain `akka-actor-typed_2.13:2.10.23`. The Akka test is prepared but uncompiled/unrun, not skipped or passed. See the [decision record](../docs/decisions/0002-integration-probes.md).

Spring AI uses the real Ollama adapter and Boot auto-configuration with a synthetic loopback HTTP server. No Ollama daemon, model weights, credentials or remote inference are needed. The test verifies request serialization, response parsing and the configured model name; it does not establish actual-model behavior, streaming, embeddings or tool calling.

Akka's test is intended to verify a Spring-managed typed ActorSystem, bounded asynchronous request/reply and shutdown. Authorized repository access must first be configured in an active profile in user-local Maven settings (outside this repository), following Akka's official account instructions. No repository token, tokenized URL or license key belongs in a POM, command argument, committed file or chat. Maven output may contain repository URLs: treat any credential-bearing output as sensitive and redact it before sharing. After access changes, use Maven `-U` if the previous failed resolution is cached; that retry has not been run.

Surefire reports, dependency trees and empty test-only JARs are generated beneath each project's ignored `target/`. The application scaffold checks were not repeated. The known sandbox execution-helper failure required approved outside-sandbox commands for these runs; no Administrator shell is an application requirement.

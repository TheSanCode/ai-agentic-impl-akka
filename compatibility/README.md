# Isolated dependency probes

These standalone Maven test projects use the scaffold's Boot 4.0.8 / Java 25 baseline. They are not modules or dependencies of the application. No agents, workflows, real models or enterprise sources are implemented.

From the repository root in PowerShell:

```powershell
$env:JAVA_HOME = Join-Path $env:LOCALAPPDATA 'Programs/Eclipse Adoptium/jdk-25.0.4.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$mavenVersion = .\mvnw.cmd --version
$mavenVersion
if (($mavenVersion -join "`n") -notmatch 'Java version:\s+25(?:\.|,)') {
    throw 'Maven is not running on Java 25; check JAVA_HOME and PATH in this same PowerShell session.'
}
.\mvnw.cmd -B -ntp -f compatibility\spring-ai\pom.xml verify
.\mvnw.cmd -B -ntp -f compatibility\spring-ai\pom.xml dependency:tree '-DoutputFile=target/dependency-tree.txt'
.\scripts\Test-AkkaCompatibility.ps1 -ValidateOnly
.\scripts\Test-AkkaCompatibility.ps1
.\scripts\Test-AkkaCompatibility.ps1 -Probe combined
.\scripts\Test-AkkaCompatibility.ps1 -Probe combined -IsolatedCache
```

Run the entire block in the same PowerShell session; the wrapper selects its JVM from that session's `JAVA_HOME`/`PATH`. The guard stops before builds if Maven does not report Java 25. A Java 21 `release version 25 not supported` error means this setup block was not effective for that Maven process.

Spring AI passed **1 test** and dependency-tree generation passed. The Akka probe passed **1 test on Temurin 25**, using Akka 2.10.23 available in the local Maven cache; its typed ask/reply and termination path ran successfully. Maven warned that the requested `akka-repository` profile was not active in the successful run, so that run was cache-backed only. Fresh vendor-repository resolution was later verified with `-Probe combined -IsolatedCache` (8 October 2026). If the profile warning appears, treat a result as cache-backed only; see the [decision record](../docs/decisions/0002-integration-probes.md).

Spring AI uses the real Ollama adapter and Boot auto-configuration with a synthetic loopback HTTP server. No Ollama daemon, model weights, credentials or remote inference are needed. The test verifies request serialization, response parsing and the configured model name; it does not establish actual-model behavior, streaming, embeddings or tool calling.

Akka's dedicated script selects JDK 25, verifies the Maven JVM, and activates the POM's `akka-repository` profile. It uses a temporary empty Maven settings file so stale or misnamed profiles in user settings cannot override the probe configuration; the temporary file is removed afterward. `-ValidateOnly` checks Java and profile selection without repository access. For the actual test, the script securely prompts for the vendor-authorized HTTPS URL if `AKKA_REPOSITORY_URL` is not already set; the secure prompt does not echo input. The URL is not stored in the repository or passed as a command argument, and script output redacts URLs. A user-settings repository profile is not needed by this probe. `-IsolatedCache` resolves into an empty temporary local repository (`-Dmaven.repo.local`), then requires `akka-actor-typed_2.13-2.10.23.jar` to be recorded as downloaded from repository ID `akka-repository`, printing `VERIFIED: ...` on success; the temporary repository is deleted afterward and the normal `~/.m2` cache is untouched. Expect a longer first run because all dependencies are downloaded.

The `combined` probe hosts both libraries in one Boot 4.0.8 context: a Spring-managed typed ActorSystem whose actor delegates a Spring AI `ChatClient` call to a bounded two-thread executor (keeping blocking model I/O off the actor dispatcher), then replies through `pipeToSelf`. The model is the same loopback Ollama fixture. On 8 October 2026 it passed **1 test on Temurin 25** offline from the local Maven cache (Akka artifacts recorded as originally downloaded from repository ID `akka-repository`); `dependency:tree` showed a single `slf4j-api` 2.0.18, Scala library 2.13.17 and no Jackson from Akka core. This proves co-existence and actor-to-model delegation only, not agent workflows, cancellation, persistence or recovery.

Surefire reports, dependency trees and empty test-only JARs are generated beneath each project's ignored `target/`. The application scaffold checks were not repeated. The known sandbox execution-helper failure required approved outside-sandbox commands for these runs; no Administrator shell is an application requirement.

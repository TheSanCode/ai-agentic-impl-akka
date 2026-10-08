# Isolated dependency probes

These standalone Maven test projects use the scaffold's Boot 4.0.8 / Java 25 baseline. They are not modules or dependencies of the application. No agents, workflows, real models or enterprise sources are implemented.

From the repository root in PowerShell:

```powershell
$env:JAVA_HOME = Join-Path $env:LOCALAPPDATA 'Programs/Eclipse Adoptium/jdk-25.0.4.1+1'
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
.\mvnw.cmd -B -ntp -f compatibility/spring-ai/pom.xml verify
.\mvnw.cmd -B -ntp -f compatibility/spring-ai/pom.xml dependency:tree '-DoutputFile=target/dependency-tree.txt'
$settings = Join-Path $HOME '.m2\settings.xml'
.\mvnw.cmd -s $settings -P akka-repository -U -B -ntp -f compatibility/akka/pom.xml verify
```

Spring AI passed **1 test** and dependency-tree generation passed. The Akka probe later passed **1 test on Temurin 25**, using Akka 2.10.23 available in the local Maven cache; its typed ask/reply and termination path ran successfully. However, Maven warned that the requested `akka-repository` profile was not active. A fresh vendor-repository resolution is therefore not verified; see the [decision record](../docs/decisions/0002-integration-probes.md).

Spring AI uses the real Ollama adapter and Boot auto-configuration with a synthetic loopback HTTP server. No Ollama daemon, model weights, credentials or remote inference are needed. The test verifies request serialization, response parsing and the configured model name; it does not establish actual-model behavior, streaming, embeddings or tool calling.

Akka's test verifies a Spring-managed typed ActorSystem, bounded asynchronous request/reply and shutdown when the dependencies are locally available. To verify fresh dependency access, configure the vendor-authorized tokenized repository URL in an active profile in user-local Maven settings (outside this repository), with repository ID `akka-repository`; confirm Maven reports that profile active. The command above selects that profile and settings file and forces retry after cached resolution. Do not put the tokenized URL or license key in a POM, command argument, committed file or chat. Maven output may contain credential-bearing repository URLs; redact them before sharing.

Surefire reports, dependency trees and empty test-only JARs are generated beneath each project's ignored `target/`. The application scaffold checks were not repeated. The known sandbox execution-helper failure required approved outside-sandbox commands for these runs; no Administrator shell is an application requirement.

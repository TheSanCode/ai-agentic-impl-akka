package io.agenticawithakka.config;

import io.agenticawithakka.agents.Coordinator;
import io.agenticawithakka.agents.InvestigationAgent;
import io.agenticawithakka.application.ports.DelegatedTokenProvider;
import io.agenticawithakka.application.ports.PolicyDecisionService;
import io.agenticawithakka.application.ports.SearchGateway;
import io.agenticawithakka.application.ports.SourceConnector;
import io.agenticawithakka.connectors.mock.MockDelegatedTokenProvider;
import io.agenticawithakka.connectors.mock.MockSourceConnector;
import io.agenticawithakka.domain.contracts.Classification;
import io.agenticawithakka.domain.contracts.EvidencePassage;
import io.agenticawithakka.domain.contracts.EvidenceRef;
import io.agenticawithakka.domain.contracts.ProjectId;
import io.agenticawithakka.observability.ExecutionTrace;
import io.agenticawithakka.retrieval.MockSearchGateway;
import io.agenticawithakka.security.MockIdentityContextStore;
import io.agenticawithakka.security.MockProjectPolicyService;
import io.agenticawithakka.security.StoredTaskResultAuthorizer;
import io.agenticawithakka.tools.DelegationTarget;
import io.agenticawithakka.tools.PhaseOneReadTools;
import io.agenticawithakka.tools.ToolRegistry;
import io.agenticawithakka.workflow.ProcessLocalInvestigationService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition of the process-local coordinator and synthetic read-only adapters. */
@Configuration(proxyBeanMethods = false)
public class InvestigationConfiguration {
    private static final String KNOWLEDGE = "mock-knowledge";
    private static final String LOGS = "mock-logs";
    private static final String HEALTH = "mock-health";

    @Bean
    PolicyDecisionService policyDecisionService(MockIdentityContextStore identities) {
        return new MockProjectPolicyService(identities);
    }

    @Bean
    SearchGateway mockSearchGateway(MockIdentityContextStore identities, ServiceProperties properties, Clock clock) {
        List<EvidencePassage> seeded = projectIds(properties).stream()
                .map(project -> passage(
                        KNOWLEDGE,
                        project,
                        "Synthetic runbook: for billing connection timeouts, inspect connection-pool saturation "
                                + "and compare the service logs with the configured pool limits.",
                        clock))
                .toList();
        return new MockSearchGateway(identities, clock, seeded);
    }

    @Bean
    byte[] mockDelegationSigningKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    @Bean
    DelegatedTokenProvider mockDelegatedTokenProvider(
            MockIdentityContextStore identities, Clock clock, byte[] mockDelegationSigningKey) {
        return new MockDelegatedTokenProvider(
                identities,
                clock,
                Duration.ofMinutes(2),
                mockDelegationSigningKey,
                Map.of(LOGS, Set.of("logs.read"), HEALTH, Set.of("health.read")));
    }

    @Bean
    SourceConnector mockLogsConnector(
            MockIdentityContextStore identities,
            ServiceProperties properties,
            Clock clock,
            byte[] mockDelegationSigningKey) {
        var seeded = projectIds(properties).stream()
                .map(project -> passage(
                        LOGS,
                        project,
                        "Synthetic billing log: connection acquisition timed out while the pool was saturated.",
                        clock))
                .toList();
        return new MockSourceConnector(
                LOGS,
                clock,
                mockDelegationSigningKey,
                Map.of("queryLogs", "logs.read"),
                Map.of("queryLogs", seeded),
                subjectsBySource(properties, LOGS),
                identities);
    }

    @Bean
    SourceConnector mockHealthConnector(
            MockIdentityContextStore identities,
            ServiceProperties properties,
            Clock clock,
            byte[] mockDelegationSigningKey) {
        var seeded = projectIds(properties).stream()
                .map(project -> passage(
                        HEALTH,
                        project,
                        "Synthetic health report: the billing endpoint responds but connection-pool pressure is elevated.",
                        clock))
                .toList();
        return new MockSourceConnector(
                HEALTH,
                clock,
                mockDelegationSigningKey,
                Map.of("inspectHealth", "health.read"),
                Map.of("inspectHealth", seeded),
                subjectsBySource(properties, HEALTH),
                identities);
    }

    @Bean
    ToolRegistry phaseOneToolRegistry(
            SearchGateway search,
            SourceConnector mockLogsConnector,
            SourceConnector mockHealthConnector,
            DelegatedTokenProvider tokens,
            PolicyDecisionService policy,
            Clock clock) {
        return PhaseOneReadTools.registry(
                search,
                mockLogsConnector,
                new DelegationTarget(LOGS, Set.of("logs.read")),
                mockHealthConnector,
                new DelegationTarget(HEALTH, Set.of("health.read")),
                tokens,
                policy,
                clock);
    }

    @Bean
    StoredTaskResultAuthorizer storedTaskResultAuthorizer(
            MockIdentityContextStore identities, PolicyDecisionService policy) {
        return new StoredTaskResultAuthorizer(identities, policy);
    }

    @Bean
    ExecutionTrace executionTrace() {
        return new ExecutionTrace();
    }

    @Bean
    InvestigationAgent investigationAgent(ToolRegistry tools, Clock clock) {
        return new InvestigationAgent(tools, clock);
    }

    @Bean
    Coordinator coordinator(InvestigationAgent investigation, Clock clock) {
        return new Coordinator(investigation, clock);
    }

    @Bean(destroyMethod = "shutdown")
    ThreadPoolExecutor investigationExecutor() {
        return new ThreadPoolExecutor(
                2,
                2,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32),
                runnable -> {
                    Thread thread = new Thread(runnable, "local-investigation");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    ProcessLocalInvestigationService processLocalInvestigationService(
            io.agenticawithakka.security.AuthenticatedIdentityContextResolver identities,
            MockIdentityContextStore identityStore,
            PolicyDecisionService policy,
            StoredTaskResultAuthorizer resultAuthorizer,
            Coordinator coordinator,
            ThreadPoolExecutor investigationExecutor,
            ExecutionTrace executionTrace,
            Clock clock) {
        return new ProcessLocalInvestigationService(
                identities,
                identityStore,
                policy,
                resultAuthorizer,
                coordinator,
                investigationExecutor,
                executionTrace,
                clock);
    }

    private static List<ProjectId> projectIds(ServiceProperties properties) {
        return properties.security().principals().stream()
                .flatMap(principal -> principal.projects().keySet().stream())
                .distinct()
                .map(ProjectId::new)
                .toList();
    }

    private static Map<ProjectId, Set<String>> subjectsBySource(ServiceProperties properties, String source) {
        Map<ProjectId, Set<String>> result = new HashMap<>();
        for (var principal : properties.security().principals()) {
            String subject = principal.issuer() + "\n" + principal.subject();
            principal.projects().forEach((project, access) -> {
                if (access.sources().contains(source)) {
                    result.computeIfAbsent(new ProjectId(project), ignored -> new HashSet<>()).add(subject);
                }
            });
        }
        return result;
    }

    private static EvidencePassage passage(String source, ProjectId project, String text, Clock clock) {
        return new EvidencePassage(
                new EvidenceRef(
                        source,
                        "synthetic-v1",
                        project,
                        Classification.INTERNAL,
                        clock.instant(),
                        "permission:" + project.value() + "-" + source),
                text);
    }
}

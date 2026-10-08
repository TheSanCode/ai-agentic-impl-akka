package io.agenticawithakka.tools;

import static io.agenticawithakka.tools.ToolTestSupport.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agenticawithakka.domain.contracts.AgentRole;
import io.agenticawithakka.domain.contracts.ContractViolationException;
import io.agenticawithakka.domain.contracts.ToolArguments;
import io.agenticawithakka.domain.contracts.ToolRef;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ToolDefinitionTest {
    private static final ToolDefinition LOGS = QueryMockLogsTool.DEFINITION;

    private static Map<String, String> validLogs() {
        var values = new HashMap<String, String>();
        values.put("service", "alpha-api");
        values.put("from", NOW.minus(Duration.ofHours(1)).toString());
        values.put("to", NOW.toString());
        return values;
    }

    private static Map<String, String> with(String key, String value) {
        var values = validLogs();
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
        return values;
    }

    @Test
    void acceptsValidArguments() {
        var arguments = new ToolArguments(with("contains", "timeout"));
        assertThat(LOGS.validate(arguments, NOW)).isSameAs(arguments);
    }

    static Stream<Arguments> invalidLogArguments() {
        return Stream.of(
                Arguments.of(with("projectId", "beta"), "arguments"),
                Arguments.of(with("userId", "admin"), "arguments"),
                Arguments.of(with("service", null), "arguments.service"),
                Arguments.of(with("service", "../etc/passwd"), "arguments.service"),
                Arguments.of(with("service", "a".repeat(64)), "arguments.service"),
                Arguments.of(with("from", "yesterday"), "arguments.from"),
                Arguments.of(with("to", NOW.minus(Duration.ofHours(2)).toString()), "arguments.to"),
                Arguments.of(with("from", NOW.minus(Duration.ofHours(7)).toString()), "arguments.to"),
                Arguments.of(with("to", NOW.plus(Duration.ofMinutes(5)).toString()), "arguments.to"),
                Arguments.of(with("contains", "x".repeat(201)), "arguments.contains"));
    }

    @ParameterizedTest
    @MethodSource("invalidLogArguments")
    void rejectsInvalidArgumentsWithoutEchoingValues(Map<String, String> values, String field) {
        assertThatThrownBy(() -> LOGS.validate(new ToolArguments(values), NOW))
                .isInstanceOfSatisfying(ContractViolationException.class, e -> assertThat(e.field()).isEqualTo(field))
                .message()
                .doesNotContain("../etc/passwd", "yesterday", "admin");
    }

    @Test
    void rejectsLookbackBeyondLimit() {
        var old = NOW.minus(Duration.ofDays(31));
        var values = with("from", old.toString());
        values.put("to", old.plus(Duration.ofHours(1)).toString());
        assertThatThrownBy(() -> LOGS.validate(new ToolArguments(values), NOW))
                .isInstanceOfSatisfying(ContractViolationException.class,
                        e -> assertThat(e.field()).isEqualTo("arguments.from"));
    }

    @Test
    void validatesIntegerRange() {
        var search = SearchKnowledgeTool.DEFINITION;
        assertThat(search.validate(new ToolArguments(Map.of("query", "timeouts", "maxResults", "10")), NOW))
                .isNotNull();
        for (String bad : List.of("0", "11", "ten", "99999999999999999999")) {
            assertThatThrownBy(() -> search.validate(
                    new ToolArguments(Map.of("query", "timeouts", "maxResults", bad)), NOW))
                    .isInstanceOf(ContractViolationException.class);
        }
    }

    static Stream<Runnable> invalidMetadata() {
        var ref = new ToolRef("sample", 1);
        var roles = Set.of(AgentRole.INVESTIGATION);
        var service = List.of(ArgumentSpec.reference("service", true, true));
        var ok = Duration.ofSeconds(5);
        return Stream.of(
                () -> new ToolDefinition(ref, " ", ToolRisk.READ, roles, service, null, ok, 0, 1, 1),
                () -> new ToolDefinition(ref, "d", ToolRisk.READ, Set.of(), service, null, ok, 0, 1, 1),
                () -> new ToolDefinition(ref, "d", ToolRisk.READ, roles,
                        List.of(ArgumentSpec.text("a", true, 5), ArgumentSpec.text("a", false, 5)), null, ok, 0, 1, 1),
                () -> new ToolDefinition(ref, "d", ToolRisk.READ, roles, service,
                        new TimeWindow("from", "to", ok, ok), ok, 0, 1, 1),
                () -> new ToolDefinition(ref, "d", ToolRisk.READ, roles, service, null, Duration.ZERO, 0, 1, 1),
                () -> new ToolDefinition(ref, "d", ToolRisk.READ, roles, service, null, Duration.ofMinutes(3), 0, 1, 1),
                () -> new ToolDefinition(ref, "d", ToolRisk.READ, roles, service, null, ok, 4, 1, 1),
                () -> new ToolDefinition(ref, "d", ToolRisk.READ, roles, service, null, ok, 0, 51, 1),
                () -> new ToolDefinition(ref, "d", ToolRisk.READ, roles, service, null, ok, 0, 1, 100_001),
                () -> new ArgumentSpec("query", ArgumentType.TEXT, true, 10, 0, 0, true),
                () -> ArgumentSpec.integer("n", true, 5, 1));
    }

    @ParameterizedTest
    @MethodSource("invalidMetadata")
    void rejectsInvalidMetadata(Runnable construction) {
        assertThatThrownBy(construction::run).isInstanceOf(ContractViolationException.class);
    }
}

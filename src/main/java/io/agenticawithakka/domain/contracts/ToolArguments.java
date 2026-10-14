package io.agenticawithakka.domain.contracts;

import java.util.Map;

/**
 * Structurally bounded tool arguments. Per-tool schema validation is performed by the tool
 * registry before a {@link ToolRequest} is created.
 */
public record ToolArguments(Map<String, String> values) {
    public static final int MAX_ENTRIES = 32;
    public static final int MAX_VALUE_LENGTH = 1_024;

    public ToolArguments {
        values = ContractValidation.map(values, "arguments", MAX_ENTRIES);
        for (var entry : values.entrySet()) {
            ContractValidation.matches(entry.getKey(), "arguments.key", ContractValidation.CAMEL_NAME);
            ContractValidation.text(entry.getValue(), "arguments.value", MAX_VALUE_LENGTH);
        }
    }

    public static ToolArguments none() {
        return new ToolArguments(Map.of());
    }
}

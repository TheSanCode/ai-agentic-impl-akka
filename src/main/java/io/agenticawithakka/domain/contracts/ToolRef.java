package io.agenticawithakka.domain.contracts;

/** Registered tool identifier and version. */
public record ToolRef(String id, int version) {
    public ToolRef {
        ContractValidation.matches(id, "tool.id", ContractValidation.CAMEL_NAME);
        ContractValidation.positive(version, "tool.version");
    }
}

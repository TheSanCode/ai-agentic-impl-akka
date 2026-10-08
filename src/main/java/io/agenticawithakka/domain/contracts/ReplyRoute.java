package io.agenticawithakka.domain.contracts;

/**
 * Serializable routing identifier resolved by the runtime adapter. Runtime references such as
 * actor refs stay inside the adapter and are never serialized into contracts.
 */
public record ReplyRoute(String value) {
    public ReplyRoute {
        ContractValidation.matches(value, "replyRoute", ContractValidation.REFERENCE);
    }
}

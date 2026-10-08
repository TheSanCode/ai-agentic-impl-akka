package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.ContractValidation;
import io.agenticawithakka.domain.contracts.ContractViolationException;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.ProjectId;
import java.time.Instant;

/** Project- and identity-scoped knowledge search. */
public record SearchQuery(
        ProjectId projectId, IdentityContextRef identityContextRef, String text, int maxResults, Instant deadline) {
    public static final int MAX_TEXT = 2_000;
    public static final int MAX_RESULTS = 50;

    public SearchQuery {
        ContractValidation.required(projectId, "projectId");
        ContractValidation.required(identityContextRef, "identityContextRef");
        ContractValidation.text(text, "text", MAX_TEXT);
        ContractValidation.positive(maxResults, "maxResults");
        if (maxResults > MAX_RESULTS) {
            throw new ContractViolationException(
                    "maxResults", "exceeds " + MAX_RESULTS);
        }
        ContractValidation.required(deadline, "deadline");
    }
}

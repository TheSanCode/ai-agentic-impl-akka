package io.agenticawithakka.domain.contracts;

import java.util.List;

/** A structured finding. Model confidence never substitutes for cited evidence. */
public record Finding(FindingKind kind, String statement, List<EvidenceRef> evidenceRefs) {
    public static final int MAX_STATEMENT = 4_000;
    public static final int MAX_REFS = 50;

    public Finding {
        ContractValidation.required(kind, "finding.kind");
        ContractValidation.text(statement, "finding.statement", MAX_STATEMENT);
        evidenceRefs = ContractValidation.list(evidenceRefs, "finding.evidenceRefs", MAX_REFS);
        if (kind == FindingKind.FACT && evidenceRefs.isEmpty()) {
            throw new ContractViolationException("finding.evidenceRefs", "facts require evidence");
        }
    }
}

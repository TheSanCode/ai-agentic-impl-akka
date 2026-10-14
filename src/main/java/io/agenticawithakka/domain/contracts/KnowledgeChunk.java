package io.agenticawithakka.domain.contracts;

/** One bounded, provenance-preserving text chunk from an authorized source document. */
public record KnowledgeChunk(String chunkId, EvidenceRef evidenceRef, String text) {
    public KnowledgeChunk {
        ContractValidation.matches(chunkId, "chunkId", ContractValidation.REFERENCE);
        ContractValidation.required(evidenceRef, "evidenceRef");
        ContractValidation.text(text, "chunk.text", EvidencePassage.MAX_LENGTH);
    }

    public EvidencePassage passage() {
        return new EvidencePassage(evidenceRef, text);
    }
}

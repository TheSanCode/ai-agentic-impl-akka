/**
 * Versioned, framework-free domain contracts (schema version {@value TaskEnvelope#SCHEMA_VERSION}).
 * Constructors reject malformed values with {@link ContractViolationException}. No Spring, Akka,
 * HTTP, serialization-library or credential types may appear here.
 */
package io.agenticawithakka.domain.contracts;

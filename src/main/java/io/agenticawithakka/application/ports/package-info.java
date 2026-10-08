/**
 * Replaceable ports (ARC-01). Signatures use only domain contracts and JDK types; Spring AI, Akka,
 * HTTP and vendor SDK types belong in adapters. All I/O ports are asynchronous so actor
 * dispatchers are never blocked. ActionExecutor, ApprovalRepository, ExecutionRepository and
 * VerificationScheduler are deferred to the phases that implement them.
 */
package io.agenticawithakka.application.ports;

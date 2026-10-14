/**
 * Controlled tool registry and read-only tool adapters (P1-06; see decision 0004).
 *
 * <p>Tools reach sources only through application ports. {@link io.agenticawithakka.tools.ToolRegistry}
 * is the single gate for discovery and invocation; handlers are never called directly by agents.
 */
package io.agenticawithakka.tools;

package io.agenticawithakka.tools;

/** Capability class of a tool (SCP-03). A read-only installation enables only {@link #READ}. */
public enum ToolRisk {
    READ,
    DRAFT,
    WRITE
}

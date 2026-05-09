package com.postman.mcp

import java.io.File

/**
 * Describes a config file location that understands MCP server configuration.
 *
 * All JetBrains AI features (AI Assistant, Junie, Codex, Claude, GitHub Copilot for JetBrains,
 * and any future additions) share a single MCP config file at ~/.ai/mcp/mcp.json.
 * The path is resolved by JetBrains via the registry key
 * "llm.mcp.client.global.mcp.json.path" (default: .ai/mcp/mcp.json) relative to the user home.
 *
 * To add support for a new config location, add an object here and include it in ALL.
 */
sealed class McpAgentTarget(
    val displayName: String,
    /** Root key used in this location's mcp.json (e.g. "mcpServers" or "servers"). */
    val rootKey: String,
) {
    abstract fun getConfigFile(): File

    /**
     * Adapts the canonical server entry to whatever shape this location expects.
     * Default is a no-op; override to add/transform fields.
     */
    open fun adaptEntry(baseEntry: Map<String, Any>): Map<String, Any> = baseEntry

    // ── Config locations ──────────────────────────────────────────────────────────

    /**
     * Covers all JetBrains AI features: AI Assistant, Junie, Codex, Claude, GitHub Copilot
     * for JetBrains, and any future AI agents. They all share this single config file via
     * the JetBrains platform MCP infrastructure.
     */
    object JetBrains : McpAgentTarget(
        displayName = "All JetBrains AI agents (AI Assistant, Junie, GitHub Copilot…)",
        rootKey = "mcpServers",
    ) {
        override fun getConfigFile(): File {
            // Tests inject a temporary path via this system property to avoid touching ~/.ai/mcp/mcp.json
            val override = System.getProperty("com.postman.mcp.test.configFile")
            if (override != null) return File(override)
            return File(System.getProperty("user.home"), ".ai/mcp/mcp.json")
        }
    }

    // ── Registry ──────────────────────────────────────────────────────────────────

    companion object {
        // getter rather than stored val to avoid JetBrains INSTANCE being null during PathClassLoader init
        val ALL: List<McpAgentTarget> get() = listOf(JetBrains)
    }
}

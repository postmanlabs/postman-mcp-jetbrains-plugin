package com.postman.mcp

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Verifies that the single JetBrains target covers all required AI agents:
 * JetBrains AI Assistant, Junie, Codex, and GitHub Copilot for JetBrains.
 *
 * GitHub Copilot for JetBrains has no MCP infrastructure of its own; it uses
 * the JetBrains platform MCP layer which reads from ~/.ai/mcp/mcp.json.
 */
class McpAgentCoverageTest {

    @AfterEach
    fun tearDown() {
        System.clearProperty("com.postman.mcp.test.configFile")
    }

    @Test
    fun `ALL list contains exactly one target`() {
        assert(McpAgentTarget.ALL.size == 1) {
            "Expected 1 target, got ${McpAgentTarget.ALL.size}: ${McpAgentTarget.ALL}"
        }
    }

    @Test
    fun `that single target is of JetBrains type`() {
        val target = McpAgentTarget.ALL.first()
        assert(target.javaClass.name.endsWith("JetBrains")) {
            "Expected JetBrains target, got: ${target.javaClass.name}"
        }
    }

    @Test
    fun `JetBrains target uses mcpServers as root key`() {
        assert(McpAgentTarget.JetBrains.rootKey == "mcpServers") {
            "Expected mcpServers, got: ${McpAgentTarget.JetBrains.rootKey}"
        }
    }

    @Test
    fun `JetBrains config file resolves to dot-ai mcp mcp-json under user home`() {
        val expected = File(System.getProperty("user.home"), ".ai/mcp/mcp.json")
        assert(McpAgentTarget.JetBrains.getConfigFile() == expected) {
            "Expected ${expected.absolutePath}, got ${McpAgentTarget.JetBrains.getConfigFile().absolutePath}"
        }
    }

    @Test
    fun `test system property redirects getConfigFile`() {
        val override = File("/tmp/test-mcp.json")
        System.setProperty("com.postman.mcp.test.configFile", override.absolutePath)
        assert(McpAgentTarget.JetBrains.getConfigFile() == override)
    }

    @Test
    fun `display name mentions AI Assistant`() {
        assertMentions(McpAgentTarget.JetBrains.displayName, "AI Assistant")
    }

    @Test
    fun `display name mentions GitHub Copilot`() {
        assertMentions(McpAgentTarget.JetBrains.displayName, "GitHub Copilot")
    }

    @Test
    fun `GitHub Copilot for JetBrains is covered by the JetBrains target - no separate target`() {
        // GitHub Copilot for JetBrains uses the JetBrains platform MCP infrastructure.
        // Confirmed via JAR inspection: no MCP classes in the Copilot plugin JAR.
        // A single entry in ~/.ai/mcp/mcp.json makes the server available to Copilot.
        val copilotHasOwnTarget = McpAgentTarget.ALL.any { target ->
            target.displayName.contains("Copilot", ignoreCase = true) &&
                target.javaClass.name != McpAgentTarget.JetBrains.javaClass.name
        }
        assert(!copilotHasOwnTarget) {
            "Expected Copilot to be covered by the JetBrains target, not a separate one"
        }
        assert(McpAgentTarget.ALL.any { it.rootKey == "mcpServers" }) {
            "Expected mcpServers target to cover GitHub Copilot"
        }
    }

    @Test
    fun `adaptEntry is a transparent pass-through`() {
        val entry = mapOf<String, Any>("url" to "https://example.com", "headers" to mapOf("x" to "y"))
        assert(McpAgentTarget.JetBrains.adaptEntry(entry) == entry)
    }

    private fun assertMentions(text: String, keyword: String) {
        assert(text.contains(keyword, ignoreCase = true)) {
            "Expected '$keyword' in display name: $text"
        }
    }
}

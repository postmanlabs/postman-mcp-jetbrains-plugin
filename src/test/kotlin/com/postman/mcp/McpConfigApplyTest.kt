package com.postman.mcp

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Tests the MCP config writing logic.
 * Always writes a stdio entry pointing at the bundled Node + pre-installed MCP server.
 */
class McpConfigApplyTest {

    @TempDir
    lateinit var tmpDir: Path
    private lateinit var configFile: File

    @BeforeEach
    fun setUp() {
        configFile = tmpDir.resolve("mcp.json").toFile()
        System.setProperty("com.postman.mcp.test.configFile", configFile.absolutePath)
    }

    @AfterEach
    fun tearDown() {
        System.clearProperty("com.postman.mcp.test.configFile")
    }

    // ── stdio entry ───────────────────────────────────────────────────────────

    @Test
    fun `writes node command pointing at the bundled binary and server entry`() {
        McpConfigWriter.applyConfig("my-api-key").getOrThrow()

        val json = configFile.readText()
        assertContains(json, FAKE_NODE.absolutePath)
        assertContains(json, FAKE_SERVER.absolutePath)
    }

    @Test
    fun `does not write npx anymore`() {
        McpConfigWriter.applyConfig("key").getOrThrow()

        val json = configFile.readText()
        assert(!json.contains("\"npx\"")) { "Expected no npx reference in: $json" }
        assert(!json.contains("@postman/postman-mcp-server@latest")) {
            "Expected no @latest reference in: $json"
        }
    }

    @Test
    fun `api key is written to env block`() {
        McpConfigWriter.applyConfig("my-api-key").getOrThrow()

        val json = configFile.readText()
        assertContains(json, "POSTMAN_API_KEY")
        assertContains(json, "my-api-key")
    }

    @Test
    fun `blank api key omits env block entirely`() {
        McpConfigWriter.applyConfig("").getOrThrow()

        assert(!configFile.readText().contains("POSTMAN_API_KEY")) {
            "Expected no env block when key is blank"
        }
    }

    @Test
    fun `no extra cli flags are added (minimal toolset)`() {
        McpConfigWriter.applyConfig("key").getOrThrow()

        val json = configFile.readText()
        assert(!json.contains("--full") && !json.contains("--code")) {
            "Expected no toolset flags: $json"
        }
    }

    // ── Config structure ──────────────────────────────────────────────────────

    @Test
    fun `config is written under mcpServers with postman entry`() {
        McpConfigWriter.applyConfig("key").getOrThrow()

        val json = configFile.readText()
        assertContains(json, "\"mcpServers\"")
        assertContains(json, "\"postman\"")
    }

    @Test
    fun `merges with existing config and preserves other servers`() {
        configFile.parentFile?.mkdirs()
        configFile.writeText("""{"mcpServers":{"other-tool":{"url":"https://example.com"}}}""")

        McpConfigWriter.applyConfig("key").getOrThrow()

        val json = configFile.readText()
        assertContains(json, "\"other-tool\"")
        assertContains(json, "\"postman\"")
    }

    @Test
    fun `applying again overwrites previous postman entry`() {
        McpConfigWriter.applyConfig("old-key").getOrThrow()
        McpConfigWriter.applyConfig("new-key").getOrThrow()

        val json = configFile.readText()
        assertContains(json, "new-key")
        assert(!json.contains("old-key")) { "Expected old key replaced: $json" }
        assert(json.indexOf("\"postman\"") == json.lastIndexOf("\"postman\"")) {
            "Expected exactly one postman entry: $json"
        }
    }

    // ── Deep merge of user edits ────────────────────────────────────────────────

    @Test
    fun `deep merge preserves user-added args after the server entry`() {
        configFile.parentFile?.mkdirs()
        configFile.writeText(
            """{"mcpServers":{"postman":{"command":"/stale/node","args":["${FAKE_SERVER.absolutePath}","--full"],"env":{"POSTMAN_API_KEY":"k"}}}}"""
        )

        McpConfigWriter.applyConfig("k").getOrThrow()

        val json = configFile.readText()
        assertContains(json, "--full")
        // command is refreshed to the current bundled node, not the stale one.
        assertContains(json, FAKE_NODE.absolutePath)
        assert(!json.contains("/stale/node")) { "Expected stale command refreshed: $json" }
    }

    @Test
    fun `deep merge preserves user-added env vars and overwrites the api key`() {
        configFile.parentFile?.mkdirs()
        configFile.writeText(
            """{"mcpServers":{"postman":{"command":"${FAKE_NODE.absolutePath}","args":["${FAKE_SERVER.absolutePath}"],"env":{"POSTMAN_API_KEY":"old","POSTMAN_API_BASE_URL":"https://api.eu.postman.com"}}}}"""
        )

        McpConfigWriter.applyConfig("new").getOrThrow()

        val json = configFile.readText()
        assertContains(json, "POSTMAN_API_BASE_URL")
        assertContains(json, "https://api.eu.postman.com")
        assertContains(json, "new")
        assert(!json.contains("\"old\"")) { "Expected api key overwritten: $json" }
    }

    @Test
    fun `legacy npx entry is replaced wholesale, not merged`() {
        configFile.parentFile?.mkdirs()
        configFile.writeText(
            """{"mcpServers":{"postman":{"command":"npx","args":["-y","@postman/postman-mcp-server@latest"]}}}"""
        )

        McpConfigWriter.applyConfig("k").getOrThrow()

        val json = configFile.readText()
        assert(!json.contains("npx")) { "Expected legacy npx command replaced: $json" }
        assert(!json.contains("@latest")) { "Expected legacy npx args dropped: $json" }
        assertContains(json, FAKE_NODE.absolutePath)
        assertContains(json, FAKE_SERVER.absolutePath)
    }

    @Test
    fun `creates parent directories when they do not exist`() {
        val nestedFile = tmpDir.resolve("deep/nested/mcp.json").toFile()
        System.setProperty("com.postman.mcp.test.configFile", nestedFile.absolutePath)

        McpConfigWriter.applyConfig("key").getOrThrow()

        assert(nestedFile.exists()) { "Expected config file created at nested path" }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun assertContains(json: String, substring: String) {
        assert(json.contains(substring)) { "Expected '$substring' in:\n$json" }
    }
}

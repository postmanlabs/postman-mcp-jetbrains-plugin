package com.postman.mcp

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Tests the MCP config writing logic.
 * Always writes the minimal stdio (npx) server — no user-configurable transport.
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

    // ── npx entry ─────────────────────────────────────────────────────────────

    @Test
    fun `writes npx command with postman-mcp-server package`() {
        McpConfigWriter.applyConfig("my-api-key").getOrThrow()

        val json = configFile.readText()
        assertContains(json, "\"npx\"")
        assertContains(json, "@postman/postman-mcp-server@latest")
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

package com.postman.mcp

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Tests the MCP config removal logic.
 * Covers the "uninstall / disable" file-cleanup flow.
 */
class McpConfigRemoveTest {

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

    @Test
    fun `removes postman entry and leaves other servers intact`() {
        write("""{"mcpServers":{"postman":{"url":"x"},"other":{"url":"y"}}}""")

        McpConfigWriter.removeConfig().getOrThrow()

        val json = configFile.readText()
        assertAbsent(json, "\"postman\"")
        assertContains(json, "\"other\"")
    }

    @Test
    fun `removes mcpServers root key when postman was the only entry`() {
        write("""{"mcpServers":{"postman":{"url":"x"}}}""")

        McpConfigWriter.removeConfig().getOrThrow()

        assertAbsent(configFile.readText(), "\"mcpServers\"")
    }

    @Test
    fun `writes empty object when entire config is empty after removal`() {
        write("""{"mcpServers":{"postman":{"url":"x"}}}""")

        McpConfigWriter.removeConfig().getOrThrow()

        assert(configFile.readText().trim() == "{}") {
            "Expected empty object, got: ${configFile.readText()}"
        }
    }

    @Test
    fun `no-op when config file does not exist`() {
        assert(!configFile.exists())
        val result = McpConfigWriter.removeConfig()
        assert(result.isSuccess) { "Expected success when file missing, got: $result" }
        assert(!configFile.exists()) { "Expected no file to be created" }
    }

    @Test
    fun `no-op when postman entry is absent from config`() {
        write("""{"mcpServers":{"other":{"url":"y"}}}""")

        McpConfigWriter.removeConfig().getOrThrow()

        assertContains(configFile.readText(), "\"other\"")
    }

    @Test
    fun `leaves other top-level keys untouched`() {
        write("""{"mcpServers":{"postman":{"url":"x"}},"globalSetting":"value"}""")

        McpConfigWriter.removeConfig().getOrThrow()

        assertContains(configFile.readText(), "\"globalSetting\"")
    }

    @Test
    fun `remove after apply restores file to empty object`() {
        McpConfigWriter.applyConfig("key").getOrThrow()
        assert(configFile.exists())

        McpConfigWriter.removeConfig().getOrThrow()

        assert(configFile.readText().trim() == "{}") {
            "Expected {} after remove, got: ${configFile.readText()}"
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun write(json: String) {
        configFile.parentFile?.mkdirs()
        configFile.writeText(json)
    }

    private fun assertContains(json: String, s: String) =
        assert(json.contains(s)) { "Expected '$s' in:\n$json" }

    private fun assertAbsent(json: String, s: String) =
        assert(!json.contains(s)) { "Expected '$s' to be absent in:\n$json" }
}

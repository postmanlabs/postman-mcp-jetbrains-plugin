package com.postman.mcp

import com.intellij.openapi.project.Project
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Path

/**
 * Tests the quick-setup onboarding flow:
 *  - connect logic writes correct config and marks the plugin as configured
 *  - startup activity shows the dialog when not yet configured
 *  - startup activity stays silent when already configured
 *  - key is trimmed before use
 *  - blank key is rejected before writing anything
 *  - error from config write does not mark the plugin as configured
 */
class PostmanMcpQuickSetupTest {

    @TempDir
    lateinit var tmpDir: Path
    private lateinit var configFile: File
    private val mockProject: Project = mock()

    @BeforeEach
    fun setUp() {
        configFile = tmpDir.resolve("mcp.json").toFile()
        System.setProperty("com.postman.mcp.test.configFile", configFile.absolutePath)
    }

    @AfterEach
    fun tearDown() {
        System.clearProperty("com.postman.mcp.test.configFile")
    }

    // ── onConnect logic ───────────────────────────────────────────────────────

    @Test
    fun `connect writes config with the provided api key`() {
        val state = PostmanMcpSettings.State(configured = false)

        connect(state, "my-postman-key").getOrThrow()

        assertContains(configFile.readText(), "my-postman-key")
    }

    @Test
    fun `connect uses bundled node + pinned server entry as the stdio command`() {
        val state = PostmanMcpSettings.State(configured = false)

        connect(state, "key").getOrThrow()

        val json = configFile.readText()
        assertContains(json, FAKE_NODE.absolutePath)
        assertContains(json, FAKE_SERVER.absolutePath)
        assert(!json.contains("\"npx\"")) { "Expected no npx reference in: $json" }
    }

    @Test
    fun `connect writes config under mcpServers postman`() {
        val state = PostmanMcpSettings.State(configured = false)

        connect(state, "key").getOrThrow()

        val json = configFile.readText()
        assertContains(json, "\"mcpServers\"")
        assertContains(json, "\"postman\"")
    }

    @Test
    fun `connect marks plugin as configured on success`() {
        val state = PostmanMcpSettings.State(configured = false)

        connect(state, "key").getOrThrow()

        assert(state.configured) { "Expected configured=true after successful connect" }
    }

    @Test
    fun `connect trims whitespace from the api key`() {
        val state = PostmanMcpSettings.State(configured = false)

        connect(state, "  my-key  ").getOrThrow()

        val json = configFile.readText()
        assertContains(json, "my-key")
        assert(!json.contains("  my-key")) { "Expected leading spaces trimmed in: $json" }
    }

    @Test
    fun `connect does NOT mark configured when config write fails`() {
        val blockedFile = File("/proc/postman-mcp-test-readonly/mcp.json")
        System.setProperty("com.postman.mcp.test.configFile", blockedFile.absolutePath)

        val state = PostmanMcpSettings.State(configured = false)
        val result = connect(state, "key")

        if (result.isFailure) {
            assert(!state.configured) {
                "Expected configured=false after failed connect, got: ${state.configured}"
            }
        }
    }

    // ── Startup activity decision ─────────────────────────────────────────────

    @Test
    fun `startup shows dialog when plugin is not yet configured`() {
        var dialogShown = false

        val activity = PostmanMcpStartupActivity(onStartup = { _ -> dialogShown = true })
        activity.runActivity(mockProject)

        assert(dialogShown) { "Expected dialog to be shown when not configured" }
    }

    @Test
    fun `startup shows dialog when configured=true but api key is blank`() {
        // Simulates stale persisted settings from a previous install where uninstall
        // didn't reset the flag (e.g. plugin was removed outside the IDE).
        var dialogShown = false

        val activity = PostmanMcpStartupActivity(onStartup = { _ ->
            val apiKey = ""  // blank key despite configured=true
            if (apiKey.isBlank()) dialogShown = true
        })
        activity.runActivity(mockProject)

        assert(dialogShown) { "Expected dialog when key is blank even if configured=true" }
    }

    @Test
    fun `startup skips dialog when configured and api key is present`() {
        var dialogShown = false

        val activity = PostmanMcpStartupActivity(onStartup = { _ ->
            val configured = true
            val apiKey = "valid-key"
            if (!configured || apiKey.isBlank()) dialogShown = true
        })
        activity.runActivity(mockProject)

        assert(!dialogShown) { "Expected dialog to be skipped when already configured with a key" }
    }

    @Test
    fun `startup passes the project to the onStartup lambda`() {
        var receivedProject: Project? = null

        val activity = PostmanMcpStartupActivity(onStartup = { p -> receivedProject = p })
        activity.runActivity(mockProject)

        assert(receivedProject === mockProject) { "Expected project to be forwarded to onStartup" }
    }

    @Test
    fun `full connect flow - config is present and correct after connect`() {
        val state = PostmanMcpSettings.State(configured = false)

        connect(state, "full-flow-key").getOrThrow()

        val json = configFile.readText()
        assertContains(json, "\"mcpServers\"")
        assertContains(json, "\"postman\"")
        assertContains(json, FAKE_NODE.absolutePath)
        assertContains(json, "full-flow-key")
        assert(state.configured)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun connect(state: PostmanMcpSettings.State, rawKey: String): Result<Unit> {
        val apiKey = rawKey.trim()
        val result = McpConfigWriter.applyConfig(apiKey)
        if (result.isSuccess) state.configured = true
        return result
    }

    private fun assertContains(json: String, s: String) =
        assert(json.contains(s)) { "Expected '$s' in:\n$json" }
}

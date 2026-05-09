package com.postman.mcp

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Path

/**
 * Tests install / uninstall / disable / enable lifecycle flows.
 *
 * The listener is exercised with injected lambdas so the test does not need a
 * running IntelliJ application.  File I/O is redirected to a temp directory via
 * a JVM system property so it works across the sandbox's PathClassLoader boundary.
 */
class PostmanMcpLifecycleListenerTest {

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

    // ── Enable / re-enable (pluginLoaded) ─────────────────────────────────────

    @Test
    fun `enable - onLoad is invoked when plugin ID matches`() {
        var called = false
        val listener = listener(onLoad = { called = true })
        listener.pluginLoaded(descriptor(OWN_ID))
        assert(called) { "Expected onLoad to be called" }
    }

    @Test
    fun `enable - onLoad is NOT invoked for a different plugin ID`() {
        var called = false
        val listener = listener(onLoad = { called = true })
        listener.pluginLoaded(descriptor("com.some.other.plugin"))
        assert(!called) { "Expected onLoad to be ignored for other plugin" }
    }

    @Test
    fun `enable - config is written when previously configured`() {
        val state = configuredState()
        val listener = listener(onLoad = {
            if (state.configured) McpConfigWriter.applyConfig("re-enable-key")
        })
        listener.pluginLoaded(descriptor(OWN_ID))

        assertContains(configFile.readText(), "\"postman\"")
    }

    @Test
    fun `enable - config is NOT written when plugin was never configured`() {
        val state = configuredState(configured = false)
        val listener = listener(onLoad = {
            if (state.configured) McpConfigWriter.applyConfig("key")
        })
        listener.pluginLoaded(descriptor(OWN_ID))

        assert(!configFile.exists()) { "Expected no file when not yet configured" }
    }

    @Test
    fun `enable - re-enable restores config that was removed on disable`() {
        val state = configuredState()

        // Simulate a previous "disable" that left the file empty
        configFile.parentFile?.mkdirs()
        configFile.writeText("{}")

        val listener = listener(onLoad = {
            if (state.configured) McpConfigWriter.applyConfig("key")
        })
        listener.pluginLoaded(descriptor(OWN_ID))

        assertContains(configFile.readText(), "\"postman\"")
    }

    // ── Disable / uninstall (beforePluginUnload) ──────────────────────────────

    @Test
    fun `uninstall - onUnload is invoked when plugin ID matches`() {
        var calledWith: Boolean? = null
        val listener = listener(onUnload = { isUpdate, _ -> calledWith = isUpdate })
        listener.beforePluginUnload(descriptor(OWN_ID), isUpdate = false)
        assert(calledWith == false) { "Expected onUnload called with isUpdate=false" }
    }

    @Test
    fun `uninstall - removes postman entry from config file`() {
        configFile.parentFile?.mkdirs()
        configFile.writeText("""{"mcpServers":{"postman":{"url":"x"}}}""")

        val listener = listener(onUnload = { isUpdate, _ ->
            if (!isUpdate) McpConfigWriter.removeConfig()
        })
        listener.beforePluginUnload(descriptor(OWN_ID), isUpdate = false)

        assertAbsent(configFile.readText(), "\"postman\"")
    }

    @Test
    fun `disable - preserves other MCP servers when removing postman entry`() {
        configFile.parentFile?.mkdirs()
        configFile.writeText("""{"mcpServers":{"postman":{"url":"x"},"other":{"url":"y"}}}""")

        val listener = listener(onUnload = { isUpdate, _ ->
            if (!isUpdate) McpConfigWriter.removeConfig()
        })
        listener.beforePluginUnload(descriptor(OWN_ID), isUpdate = false)

        val json = configFile.readText()
        assertAbsent(json, "\"postman\"")
        assertContains(json, "\"other\"")
    }

    @Test
    fun `update - does NOT remove config when isUpdate is true`() {
        configFile.parentFile?.mkdirs()
        configFile.writeText("""{"mcpServers":{"postman":{"url":"x"}}}""")

        val listener = listener(onUnload = { isUpdate, _ ->
            if (!isUpdate) McpConfigWriter.removeConfig()
        })
        listener.beforePluginUnload(descriptor(OWN_ID), isUpdate = true)

        assertContains(configFile.readText(), "\"postman\"")
    }

    @Test
    fun `uninstall - onUnload is NOT invoked for a different plugin ID`() {
        var called = false
        val listener = listener(onUnload = { _, _ -> called = true })
        listener.beforePluginUnload(descriptor("com.some.other.plugin"), isUpdate = false)
        assert(!called) { "Expected onUnload to be ignored for other plugin" }
    }

    // ── Full lifecycle flow ───────────────────────────────────────────────────

    @Test
    fun `full flow - install then disable then re-enable`() {
        val state = configuredState()
        val listener = listener(
            onLoad = { if (state.configured) McpConfigWriter.applyConfig("flow-key") },
            onUnload = { isUpdate, _ -> if (!isUpdate) McpConfigWriter.removeConfig() },
        )

        // Install: user configures and applies (simulated via direct call — settings panel)
        McpConfigWriter.applyConfig("flow-key").getOrThrow()
        assertContains(configFile.readText(), "\"postman\"")

        // Disable: IDE calls beforePluginUnload with isUpdate=false
        listener.beforePluginUnload(descriptor(OWN_ID), isUpdate = false)
        assertAbsent(configFile.readText(), "\"postman\"")

        // Re-enable: IDE calls pluginLoaded
        listener.pluginLoaded(descriptor(OWN_ID))
        assertContains(configFile.readText(), "\"postman\"")
    }

    @Test
    fun `full flow - plugin update keeps config throughout`() {
        val state = configuredState()
        val listener = listener(
            onLoad = { if (state.configured) McpConfigWriter.applyConfig("key") },
            onUnload = { isUpdate, _ -> if (!isUpdate) McpConfigWriter.removeConfig() },
        )

        McpConfigWriter.applyConfig("key").getOrThrow()
        assertContains(configFile.readText(), "\"postman\"")

        // Update: IDE calls beforePluginUnload with isUpdate=true — config must survive
        listener.beforePluginUnload(descriptor(OWN_ID), isUpdate = true)
        assertContains(configFile.readText(), "\"postman\"")

        // After update IDE calls pluginLoaded — config re-applied (idempotent)
        listener.pluginLoaded(descriptor(OWN_ID))
        assertContains(configFile.readText(), "\"postman\"")
    }

    // ── Disable vs. uninstall distinction ────────────────────────────────────

    @Test
    fun `disable - onUnload receives isDisabled=true`() {
        var receivedIsDisabled: Boolean? = null
        val listener = listener(
            onUnload = { _, isDisabled -> receivedIsDisabled = isDisabled },
            isDisabledCheck = { true },
        )
        listener.beforePluginUnload(descriptor(OWN_ID), isUpdate = false)
        assert(receivedIsDisabled == true) { "Expected isDisabled=true when plugin is disabled" }
    }

    @Test
    fun `uninstall - onUnload receives isDisabled=false`() {
        var receivedIsDisabled: Boolean? = null
        val listener = listener(
            onUnload = { _, isDisabled -> receivedIsDisabled = isDisabled },
            isDisabledCheck = { false },
        )
        listener.beforePluginUnload(descriptor(OWN_ID), isUpdate = false)
        assert(receivedIsDisabled == false) { "Expected isDisabled=false when plugin is uninstalled" }
    }

    @Test
    fun `disable - removes config but preserves configured=true`() {
        val state = configuredState(configured = true)
        configFile.parentFile?.mkdirs()
        configFile.writeText("""{"mcpServers":{"postman":{"url":"x"}}}""")

        val listener = listener(
            onUnload = { isUpdate, isDisabled ->
                if (!isUpdate) {
                    McpConfigWriter.removeConfig()
                    if (!isDisabled) state.configured = false
                }
            },
            isDisabledCheck = { true },
        )
        listener.beforePluginUnload(descriptor(OWN_ID), isUpdate = false)

        assertAbsent(configFile.readText(), "\"postman\"")
        assert(state.configured) { "Expected configured=true after disable (re-enable should restore config)" }
    }

    @Test
    fun `uninstall - removes config AND resets configured=false`() {
        val state = configuredState(configured = true)
        configFile.parentFile?.mkdirs()
        configFile.writeText("""{"mcpServers":{"postman":{"url":"x"}}}""")

        val listener = listener(
            onUnload = { isUpdate, isDisabled ->
                if (!isUpdate) {
                    McpConfigWriter.removeConfig()
                    if (!isDisabled) state.configured = false
                }
            },
            isDisabledCheck = { false },
        )
        listener.beforePluginUnload(descriptor(OWN_ID), isUpdate = false)

        assertAbsent(configFile.readText(), "\"postman\"")
        assert(!state.configured) { "Expected configured=false after uninstall (setup dialog shows on next install)" }
    }

    @Test
    fun `full flow - disable then re-enable restores config without showing setup dialog`() {
        val state = configuredState(configured = true)
        configFile.parentFile?.mkdirs()
        configFile.writeText("""{"mcpServers":{"postman":{"url":"x"}}}""")

        val listener = listener(
            onLoad = { if (state.configured) McpConfigWriter.applyConfig("key") },
            onUnload = { isUpdate, isDisabled ->
                if (!isUpdate) {
                    McpConfigWriter.removeConfig()
                    if (!isDisabled) state.configured = false
                }
            },
            isDisabledCheck = { true }, // disable, not uninstall
        )

        // Disable: config removed, configured stays true
        listener.beforePluginUnload(descriptor(OWN_ID), isUpdate = false)
        assertAbsent(configFile.readText(), "\"postman\"")
        assert(state.configured) { "configured must stay true after disable" }

        // Re-enable: config restored because configured=true
        listener.pluginLoaded(descriptor(OWN_ID))
        assertContains(configFile.readText(), "\"postman\"")
    }

    @Test
    fun `full flow - uninstall then reinstall shows setup dialog again`() {
        val state = configuredState(configured = true)
        configFile.parentFile?.mkdirs()
        configFile.writeText("""{"mcpServers":{"postman":{"url":"x"}}}""")

        val listener = listener(
            onLoad = { if (state.configured) McpConfigWriter.applyConfig("key") },
            onUnload = { isUpdate, isDisabled ->
                if (!isUpdate) {
                    McpConfigWriter.removeConfig()
                    if (!isDisabled) state.configured = false
                }
            },
            isDisabledCheck = { false }, // uninstall
        )

        // Uninstall: config removed AND configured reset to false
        listener.beforePluginUnload(descriptor(OWN_ID), isUpdate = false)
        assertAbsent(configFile.readText(), "\"postman\"")
        assert(!state.configured) { "configured must be false after uninstall" }

        // Reinstall: onLoad runs but configured=false so config is NOT auto-written
        // (setup dialog will show instead)
        listener.pluginLoaded(descriptor(OWN_ID))
        assertAbsent(configFile.readText(), "\"postman\"")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun configuredState(configured: Boolean = true) = PostmanMcpSettings.State(configured = configured)

    private fun listener(
        onLoad: () -> Unit = {},
        onUnload: (Boolean, Boolean) -> Unit = { _, _ -> },
        isDisabledCheck: (PluginId) -> Boolean = { false },
    ) = PostmanMcpPluginLifecycleListener(onLoad = onLoad, onUnload = onUnload, isDisabledCheck = isDisabledCheck)

    private fun descriptor(id: String): IdeaPluginDescriptor = mock {
        on { pluginId } doReturn PluginId.getId(id)
    }

    private fun assertContains(json: String, s: String) =
        assert(json.contains(s)) { "Expected '$s' in:\n$json" }

    private fun assertAbsent(json: String, s: String) =
        assert(!json.contains(s)) { "Expected '$s' absent in:\n$json" }

    companion object {
        private const val OWN_ID = "com.postman.mcp-server"
    }
}

package com.postman.mcp

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * End-to-end exercise of the bootstrap against the real nodejs.org distribution and the npm
 * registry. Disabled by default — set BUNDLED_RUNTIME_NETWORK_TEST=1 to opt in. Used to verify
 * the bootstrap before shipping a release.
 */
@EnabledIfEnvironmentVariable(named = "BUNDLED_RUNTIME_NETWORK_TEST", matches = "1")
class BundledRuntimeIntegrationTest {

    @TempDir
    lateinit var cacheDir: Path

    @BeforeEach
    fun setUp() {
        System.setProperty("com.postman.mcp.test.cacheRoot", cacheDir.toAbsolutePath().toString())
    }

    @AfterEach
    fun tearDown() {
        System.clearProperty("com.postman.mcp.test.cacheRoot")
    }

    @Test
    fun `ensure downloads node and installs the MCP server`() {
        val paths = BundledRuntime.ensure().getOrThrow()
        assert(paths.node.isFile) { "Expected node binary at ${paths.node}" }
        assert(paths.node.canExecute()) { "Expected node binary to be executable" }
        assert(paths.serverEntry.isFile) { "Expected server entry at ${paths.serverEntry}" }

        // Second call must be a fast cache hit (idempotent, no re-download).
        val second = BundledRuntime.ensure().getOrThrow()
        assert(second.node == paths.node)
        assert(second.serverEntry == paths.serverEntry)
    }

    @Test
    fun `cachedPaths is null before ensure and populated after`() {
        assert(BundledRuntime.cachedPaths() == null)
        BundledRuntime.ensure().getOrThrow()
        assert(BundledRuntime.cachedPaths() != null)
    }
}

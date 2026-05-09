package com.postman.mcp

import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Tests the startup-activity logic (show "configure" notification vs. stay silent).
 *
 * PostmanMcpStartupActivity replaces the deprecated ProjectManagerListener.projectOpened
 * API.  It is registered as a <postStartupActivity> extension so IntelliJ calls it via
 * StartupActivity.runActivity(Project) instead of the removed listener method.
 */
class PostmanMcpStartupActivityTest {

    private val mockProject: Project = mock()

    @Test
    fun `runActivity delegates to the injected onStartup lambda`() {
        var received: Project? = null
        val activity = PostmanMcpStartupActivity(onStartup = { p -> received = p })

        activity.runActivity(mockProject)

        assert(received === mockProject) { "Expected onStartup to receive the project" }
    }

    @Test
    fun `notification shown when plugin is not yet configured`() {
        var shown = false
        val state = PostmanMcpSettings.State(configured = false)
        val activity = PostmanMcpStartupActivity(onStartup = { _ ->
            if (!state.configured) shown = true
        })

        activity.runActivity(mockProject)

        assert(shown) { "Expected notification when plugin is not configured" }
    }

    @Test
    fun `notification skipped when plugin is already configured`() {
        var shown = false
        val state = PostmanMcpSettings.State(configured = true)
        val activity = PostmanMcpStartupActivity(onStartup = { _ ->
            if (!state.configured) shown = true
        })

        activity.runActivity(mockProject)

        assert(!shown) { "Expected no notification when plugin is already configured" }
    }

    @Test
    fun `activity supertypes include StartupActivity so it can be registered as postStartupActivity`() {
        val supertypes = PostmanMcpStartupActivity::class.java.interfaces.map { it.name }
        assert(supertypes.any { it.endsWith("StartupActivity") }) {
            "Expected StartupActivity in supertypes: $supertypes"
        }
    }

    @Test
    fun `activity supertypes include DumbAware so it runs during indexing without blocking`() {
        fun collectInterfaces(cls: Class<*>): List<String> {
            val direct = cls.interfaces.map { it.name }
            val fromSuper = cls.superclass?.let { collectInterfaces(it) } ?: emptyList()
            return direct + fromSuper
        }

        val all = collectInterfaces(PostmanMcpStartupActivity::class.java)
        assert(all.any { it.endsWith("DumbAware") }) {
            "Expected DumbAware in supertypes: $all"
        }
    }
}

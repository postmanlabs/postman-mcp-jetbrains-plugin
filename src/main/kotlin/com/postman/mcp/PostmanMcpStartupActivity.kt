package com.postman.mcp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class PostmanMcpStartupActivity(
    private val onStartup: (Project) -> Unit = { project ->
        // All checks run on the EDT after the project is open and services are ready.
        ApplicationManager.getApplication().invokeLater {
            val settings = PostmanMcpSettings.getInstance()
            val service = PostmanMcpService.getInstance()

            // One-time migration: old URL-based config → force re-setup with new stdio format.
            if (settings.state.configured && service.getApiKey().isNotBlank() && hasLegacyUrlEntry()) {
                settings.state.configured = false
                service.setApiKey("")
            }

            if (!settings.state.configured || service.getApiKey().isBlank()) {
                // Guard against multiple projects firing simultaneously: only the first wins.
                if (!settings.state.configured || service.getApiKey().isBlank()) {
                    showSetupDialog(project)
                }
            } else {
                // Re-apply on every startup to keep mcp.json current (idempotent).
                service.applyConfig()
            }
        }
    },
) : StartupActivity, DumbAware {

    override fun runActivity(project: Project) = onStartup(project)
}

/** True if mcp.json postman entry uses the old URL/remote format from a previous plugin version. */
internal fun hasLegacyUrlEntry(): Boolean =
    McpAgentTarget.ALL.any { target ->
        runCatching {
            val text = target.getConfigFile().readText()
            text.contains("\"postman\"") && text.contains("\"url\"") && !text.contains("\"command\"")
        }.getOrDefault(false)
    }

internal fun showSetupDialog(project: Project) {
    val settings = PostmanMcpSettings.getInstance()
    val service = PostmanMcpService.getInstance()
    PostmanMcpQuickSetupDialog(project) { apiKey ->
        val result = McpConfigWriter.applyConfig(apiKey)
        if (result.isSuccess) {
            service.setApiKey(apiKey)
            settings.state.configured = true
            service.showConfiguredNotification()
        }
        result
    }.show()
}

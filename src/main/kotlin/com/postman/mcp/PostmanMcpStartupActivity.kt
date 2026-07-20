package com.postman.mcp

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.startup.StartupActivity

class PostmanMcpStartupActivity(
    private val onStartup: (Project) -> Unit = ::defaultStartup,
) : StartupActivity, DumbAware {

    override fun runActivity(project: Project) = onStartup(project)
}

private fun defaultStartup(project: Project) {
    ApplicationManager.getApplication().invokeLater {
        val settings = PostmanMcpSettings.getInstance()
        val service = PostmanMcpService.getInstance()

        // One-time migration: legacy URL transport or unbundled npx command → force re-setup
        // so the next applyConfig writes the new bundled-runtime form.
        if (settings.state.configured && service.getApiKey().isNotBlank() && hasLegacyConfigEntry()) {
            settings.state.configured = false
            service.setApiKey("")
        }

        if (!settings.state.configured || service.getApiKey().isBlank()) {
            // Don't show a modal dialog at startup — it would block the IDE for headless
            // verifier runs and feels invasive to users. Surface a notification with a Connect
            // action instead; the dialog only opens when the user clicks it.
            showConnectNotification(project)
            return@invokeLater
        }

        // Already configured. Re-apply with current paths; if the bundled runtime cache was
        // wiped (e.g. user cleared the IDE config dir), download it again in the background.
        val cached = BundledRuntime.cachedPaths()
        if (cached != null) {
            service.applyConfig(cached)
        } else {
            runBootstrapInBackground(project)
        }
    }
}

/** True if mcp.json holds a postman entry written by a prior plugin version. */
internal fun hasLegacyConfigEntry(): Boolean =
    McpAgentTarget.ALL.any { target ->
        runCatching {
            val configFile = target.getConfigFile()
            if (!configFile.exists()) return@runCatching false
            val parsed = McpConfigWriter.parseJson(configFile.readText())
            @Suppress("UNCHECKED_CAST")
            val servers = parsed[target.rootKey] as? Map<String, Any> ?: return@runCatching false
            @Suppress("UNCHECKED_CAST")
            val entry = servers["postman"] as? Map<String, Any> ?: return@runCatching false
            val command = entry["command"] as? String
            // Old URL-based remote transport.
            if (command == null && entry["url"] != null) return@runCatching true
            // Old npx-based stdio command (pre-bundled-runtime).
            command == "npx"
        }.getOrDefault(false)
    }

internal fun showConnectNotification(project: Project) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Postman MCP Server")
        ?.createNotification(
            "Connect Postman MCP Server",
            "Your AI agent can use your Postman workspace once you connect with a Postman API key.",
            NotificationType.INFORMATION,
        )
        ?.addAction(NotificationAction.createSimpleExpiring("Connect…") {
            showSetupDialog(project)
        })
        ?.notify(project)
}

internal fun showSetupDialog(project: Project) {
    val settings = PostmanMcpSettings.getInstance()
    val service = PostmanMcpService.getInstance()
    PostmanMcpQuickSetupDialog(project) { apiKey ->
        // Once a valid key is entered, ask for explicit telemetry consent (Accept/Decline) before
        // writing anything. Persist it first so runSetupInModal's applyConfig picks it up.
        settings.state.telemetryEnabled = askTelemetryConsent(project)
        val result = runSetupInModal(project, apiKey)
        if (result.isSuccess) {
            service.setApiKey(apiKey)
            settings.state.configured = true
            service.showConfiguredNotification()
        }
        result
    }.show()
}

/**
 * Opt-in telemetry consent (PSTAPI-1507). Shown as a blocking Accept/Decline dialog after the
 * user enters their API key. Accept → telemetry enabled; Decline (or close) → disabled.
 */
internal fun askTelemetryConsent(project: Project): Boolean =
    Messages.showYesNoDialog(
        project,
        "Enable telemetry to help Postman improve the product and provide better support.\n\n" +
            "This is optional and stays off unless you accept. You can change it any time in " +
            "Settings → Tools → Postman MCP Server.",
        "Postman MCP Server Telemetry",
        "Accept",
        "Decline",
        Messages.getQuestionIcon(),
    ) == Messages.YES

private fun runSetupInModal(project: Project, apiKey: String): Result<Unit> {
    var result: Result<Unit> = Result.failure(IllegalStateException("setup did not start"))
    ProgressManager.getInstance().run(object : Task.Modal(
        project,
        "Setting up Postman MCP Server",
        true,
    ) {
        override fun run(indicator: ProgressIndicator) {
            result = BundledRuntime.ensure(indicator).mapCatching { paths ->
                indicator.text = "Writing MCP config…"
                val service = PostmanMcpService.getInstance()
                val previousKey = service.getApiKey()
                service.setApiKey(apiKey)
                service.applyConfig(paths).getOrElse {
                    service.setApiKey(previousKey)
                    throw it
                }
            }
        }
    })
    return result
}

internal fun runBootstrapInBackground(project: Project?) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(
        project,
        "Preparing Postman MCP Server runtime",
        true,
    ) {
        override fun run(indicator: ProgressIndicator) {
            val service = PostmanMcpService.getInstance()
            BundledRuntime.ensure(indicator)
                .onSuccess { paths -> service.applyConfig(paths) }
                .onFailure { e ->
                    ApplicationManager.getApplication().invokeLater {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Postman MCP Server")
                            ?.createNotification(
                                "Postman MCP Server — setup failed",
                                "Could not prepare local runtime: ${e.message}",
                                NotificationType.ERROR,
                            )
                            ?.notify(project)
                    }
                }
        }
    })
}

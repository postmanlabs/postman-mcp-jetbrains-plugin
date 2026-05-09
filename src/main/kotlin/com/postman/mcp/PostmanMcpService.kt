package com.postman.mcp

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.LocalFileSystem

@Service(Service.Level.APP)
class PostmanMcpService {

    private val log = logger<PostmanMcpService>()
    private val credentialAttributes = CredentialAttributes("com.postman.mcp-server", "apiKey")

    fun getApiKey(): String = PasswordSafe.instance.getPassword(credentialAttributes) ?: ""

    fun setApiKey(apiKey: String) {
        PasswordSafe.instance.set(
            credentialAttributes,
            if (apiKey.isBlank()) null else Credentials("apiKey", apiKey)
        )
    }

    fun applyConfig(): Result<Unit> {
        val result = McpConfigWriter.applyConfig(getApiKey())
        result.onSuccess {
            log.info("Postman MCP config written")
            // Notify the IDE's VFS about the file change so the MCP framework picks it up
            // without requiring an IDE restart.
            LocalFileSystem.getInstance().refreshIoFiles(McpAgentTarget.ALL.map { it.getConfigFile() })
        }.onFailure { e ->
            log.error("Failed to write Postman MCP config", e)
            showNotification(
                "Postman MCP Server — config error",
                "Could not write config: ${e.message}",
                NotificationType.ERROR
            )
        }
        return result
    }

    fun removeConfig(): Result<Unit> {
        val result = McpConfigWriter.removeConfig()
        result.onSuccess {
            log.info("Postman MCP Server entry removed")
        }.onFailure { e ->
            log.error("Failed to remove Postman MCP config", e)
        }
        return result
    }

    fun showConfiguredNotification() {
        showNotification(
            "Postman MCP Server ready",
            "Accept the IDE's prompt to enable it in your AI agent. " +
                "If no prompt appears, restart the IDE once.",
            NotificationType.INFORMATION
        )
    }

    private fun showNotification(title: String, content: String, type: NotificationType) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Postman MCP Server")
                ?.createNotification(title, content, type)
                ?.notify(null)
        }
    }

    companion object {
        fun getInstance(): PostmanMcpService =
            ApplicationManager.getApplication().getService(PostmanMcpService::class.java)
    }
}

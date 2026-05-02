package com.postman.mcp

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

class PostmanMcpStartupListener : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        val settings = PostmanMcpSettings.getInstance()
        if (!settings.state.configured) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Postman MCP Server")
                ?.createNotification(
                    "Postman MCP Server",
                    "Connect JetBrains AI Assistant to your Postman workspace.",
                    NotificationType.INFORMATION
                )
                ?.addAction(NotificationAction.createSimple("Configure") {
                    ShowSettingsUtil.getInstance().showSettingsDialog(
                        project,
                        PostmanMcpConfigurable::class.java
                    )
                })
                ?.notify(project)
        }
    }
}

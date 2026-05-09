package com.postman.mcp

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class PostmanMcpQuickSetupDialog(
    project: Project,
    private val onConnect: (apiKey: String) -> Result<Unit>,
) : DialogWrapper(project) {

    private val apiKeyField = JBPasswordField()
    private val errorLabel = JBLabel("").apply { foreground = JBColor.RED; isVisible = false }

    init {
        title = "Connect to Postman"
        setOKButtonText("Connect")
        setCancelButtonText("Not now")
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row {
            label(
                "<html>Your AI agent needs a Postman API key to access your<br>" +
                    "workspaces, collections, and APIs.</html>"
            )
        }
        row("API key:") {
            cell(apiKeyField).columns(COLUMNS_LARGE)
        }
        row {
            cell(errorLabel)
        }
        row {
            link("Get your API key →") {
                BrowserUtil.browse("https://go.postman.co/settings/me/api-keys")
            }
        }
    }

    override fun doOKAction() {
        val apiKey = String(apiKeyField.password).trim()
        if (apiKey.isBlank()) {
            showError("API key is required.")
            return
        }
        val result = onConnect(apiKey)
        if (result.isSuccess) {
            super.doOKAction()
        } else {
            showError("Could not write config: ${result.exceptionOrNull()?.message ?: "unknown error"}")
        }
    }

    private fun showError(message: String) {
        errorLabel.text = message
        errorLabel.isVisible = true
        pack()
    }

    override fun getPreferredFocusedComponent(): JComponent = apiKeyField
}

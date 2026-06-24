package com.postman.mcp

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * Settings page at Settings → Tools → Postman MCP Server.
 *
 * Exposes a single masked field for the Postman API key. Applying it overwrites the
 * `POSTMAN_API_KEY` in the MCP config via [PostmanMcpService.applyConfig] — the deep-merge in
 * [McpConfigWriter] keeps any extra args/env the user added to the postman entry by hand.
 *
 * The Connect dialog ([PostmanMcpQuickSetupDialog], reached from the startup notification and
 * Tools → Update Postman API Key) is still the onboarding path; this page just makes rotating
 * the key afterwards a one-stop edit instead of re-running setup.
 */
class PostmanMcpConfigurable : Configurable {

    private val service get() = PostmanMcpService.getInstance()
    private val apiKeyField = JBPasswordField()

    override fun getDisplayName(): String = "Postman MCP Server"

    override fun createComponent(): JComponent = panel {
        row {
            label(
                "<html>The Postman API key your AI agent uses to access your<br>" +
                    "workspaces, collections, and APIs.</html>"
            )
        }
        row("API key:") {
            cell(apiKeyField).columns(COLUMNS_LARGE)
        }
        row {
            link("Get your API key →") {
                BrowserUtil.browse("https://go.postman.co/settings/me/api-keys")
            }
        }
    }.also { reset() }

    override fun isModified(): Boolean =
        String(apiKeyField.password).trim() != service.getApiKey()

    override fun apply() {
        val newKey = String(apiKeyField.password).trim()
        service.setApiKey(newKey)
        if (newKey.isNotBlank()) {
            PostmanMcpSettings.getInstance().state.configured = true
            // Rewrite the config so POSTMAN_API_KEY reflects the new key. cachedPaths() is present
            // for any already-configured user; if the runtime cache was wiped, the next IDE startup
            // re-bootstraps and applies the saved key, so a failure here is non-fatal.
            service.applyConfig()
        }
    }

    override fun reset() {
        apiKeyField.text = service.getApiKey()
    }

    override fun getPreferredFocusedComponent(): JComponent = apiKeyField
}

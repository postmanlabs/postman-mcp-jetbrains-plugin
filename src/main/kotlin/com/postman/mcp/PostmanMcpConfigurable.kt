package com.postman.mcp

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.FormBuilder
import javax.swing.*

class PostmanMcpConfigurable : Configurable {

    private var panel: JPanel? = null
    private val connectionTypeCombo = ComboBox(PostmanMcpSettings.ConnectionType.values())
    private val toolSetCombo = ComboBox(PostmanMcpSettings.ToolSet.values())
    private val regionCombo = ComboBox(PostmanMcpSettings.Region.values())
    private val apiKeyField = JBPasswordField()
    private val apiKeyLabel = JBLabel("Postman API key:")
    private val applyButton = JButton("Write MCP config now")
    private val configPathLabel = JBLabel("")

    override fun getDisplayName() = "Postman MCP Server"

    override fun createComponent(): JComponent {
        apiKeyField.columns = 40
        updateApiKeyVisibility()

        connectionTypeCombo.addActionListener {
            updateApiKeyVisibility()
        }

        applyButton.addActionListener {
            apply()
        }

        val service = PostmanMcpService.getInstance()
        configPathLabel.text = "Config file: ${service.getMcpConfigFile().absolutePath}"

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Connection type:", connectionTypeCombo)
            .addLabeledComponent("Tool set:", toolSetCombo)
            .addLabeledComponent("Region:", regionCombo)
            .addLabeledComponent(apiKeyLabel, apiKeyField)
            .addComponent(JSeparator())
            .addComponent(configPathLabel)
            .addComponent(applyButton)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return panel!!
    }

    private fun updateApiKeyVisibility() {
        val needsKey = connectionTypeCombo.selectedItem != PostmanMcpSettings.ConnectionType.REMOTE_OAUTH
        apiKeyLabel.isVisible = needsKey
        apiKeyField.isVisible = needsKey
    }

    override fun isModified(): Boolean {
        val s = PostmanMcpSettings.getInstance().state
        return connectionTypeCombo.selectedItem != s.connectionType ||
            toolSetCombo.selectedItem != s.toolSet ||
            regionCombo.selectedItem != s.region ||
            String(apiKeyField.password) != s.apiKey
    }

    override fun apply() {
        val settings = PostmanMcpSettings.getInstance()
        settings.state.connectionType = connectionTypeCombo.selectedItem as PostmanMcpSettings.ConnectionType
        settings.state.toolSet = toolSetCombo.selectedItem as PostmanMcpSettings.ToolSet
        settings.state.region = regionCombo.selectedItem as PostmanMcpSettings.Region
        settings.state.apiKey = String(apiKeyField.password)
        settings.state.configured = true

        PostmanMcpService.getInstance().applyConfig(settings.state)
    }

    override fun reset() {
        val s = PostmanMcpSettings.getInstance().state
        connectionTypeCombo.selectedItem = s.connectionType
        toolSetCombo.selectedItem = s.toolSet
        regionCombo.selectedItem = s.region
        apiKeyField.text = s.apiKey
        updateApiKeyVisibility()
    }

    override fun disposeUIResources() {
        panel = null
    }
}

package com.postman.mcp

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId

class PostmanMcpPluginLifecycleListener(
    private val onLoad: () -> Unit = {
        val settings = PostmanMcpSettings.getInstance()
        val service = PostmanMcpService.getInstance()

        // postStartupActivity owns all setup dialog logic — it fires retroactively for
        // already-open projects when a dynamic plugin is installed. pluginLoaded only
        // re-applies config for users who are already configured.
        if (settings.state.configured && service.getApiKey().isNotBlank()) {
            service.applyConfig()
        }
    },
    // isDisabled=true  → plugin was disabled (keep configured=true so re-enable restores config)
    // isDisabled=false → plugin was uninstalled (reset configured=false so setup dialog re-runs)
    private val onUnload: (isUpdate: Boolean, isDisabled: Boolean) -> Unit = { isUpdate, isDisabled ->
        if (!isUpdate) {
            val service = PostmanMcpService.getInstance()
            service.removeConfig()
            if (!isDisabled) {
                service.setApiKey("")
                PostmanMcpSettings.getInstance().state.configured = false
            }
        }
    },
    private val isDisabledCheck: (PluginId) -> Boolean = PluginManagerCore::isDisabled,
) : DynamicPluginListener {

    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        if (pluginDescriptor.pluginId.idString == "com.postman.mcp-server") onLoad()
    }

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        if (pluginDescriptor.pluginId.idString == "com.postman.mcp-server") {
            onUnload(isUpdate, isDisabledCheck(pluginDescriptor.pluginId))
        }
    }
}

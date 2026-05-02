package com.postman.mcp

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import java.io.File

@Service(Service.Level.APP)
class PostmanMcpService {

    private val log = logger<PostmanMcpService>()

    fun applyConfig(settings: PostmanMcpSettings.State): Result<Unit> {
        return runCatching {
            val mcpJson = buildMcpJson(settings)
            val configFile = getMcpConfigFile()
            writeMcpConfig(configFile, mcpJson)
            log.info("Postman MCP Server config written to ${configFile.absolutePath}")
            showNotification(
                "Postman MCP Server configured",
                "Config written to ${configFile.absolutePath}. " +
                "Restart your IDE or reload AI Assistant to activate.",
                NotificationType.INFORMATION
            )
        }.onFailure { e ->
            log.error("Failed to write Postman MCP config", e)
            showNotification(
                "Postman MCP Server — config error",
                "Could not write config: ${e.message}",
                NotificationType.ERROR
            )
        }
    }

    fun getMcpConfigFile(): File {
        val configPath = com.intellij.openapi.application.PathManager.getConfigPath()
        return File(configPath, "mcp.json")
    }

    private fun buildMcpJson(settings: PostmanMcpSettings.State): String {
        val serverEntry = when (settings.connectionType) {
            PostmanMcpSettings.ConnectionType.LOCAL_STDIO -> buildLocalEntry(settings)
            PostmanMcpSettings.ConnectionType.REMOTE_API_KEY -> buildRemoteApiKeyEntry(settings)
            PostmanMcpSettings.ConnectionType.REMOTE_OAUTH -> buildRemoteOAuthEntry(settings)
        }

        val existing = readExistingConfig(getMcpConfigFile())
        val merged = mergeConfig(existing, "postman", serverEntry)
        return formatJson(merged)
    }

    private fun buildLocalEntry(settings: PostmanMcpSettings.State): Map<String, Any> {
        val args = mutableListOf("-y", "@postman/postman-mcp-server@latest")
        if (settings.toolSet != PostmanMcpSettings.ToolSet.MINIMAL) {
            args.add(settings.toolSet.flag)
        }
        val entry = mutableMapOf<String, Any>(
            "command" to "npx",
            "args" to args
        )
        if (settings.apiKey.isNotBlank()) {
            entry["env"] = mapOf("POSTMAN_API_KEY" to settings.apiKey)
        }
        return entry
    }

    private fun buildRemoteApiKeyEntry(settings: PostmanMcpSettings.State): Map<String, Any> {
        val url = "${settings.region.baseUrl}/${settings.toolSet.remotePath}"
        return mapOf(
            "url" to url,
            "headers" to mapOf("Authorization" to "Bearer ${settings.apiKey}")
        )
    }

    private fun buildRemoteOAuthEntry(settings: PostmanMcpSettings.State): Map<String, Any> {
        val url = "${settings.region.baseUrl}/${settings.toolSet.remotePath}"
        return mapOf("url" to url)
    }

    private fun readExistingConfig(file: File): MutableMap<String, Any> {
        if (!file.exists()) return mutableMapOf("mcpServers" to mutableMapOf<String, Any>())
        return try {
            parseSimpleJson(file.readText())
        } catch (e: Exception) {
            mutableMapOf("mcpServers" to mutableMapOf<String, Any>())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mergeConfig(
        existing: MutableMap<String, Any>,
        key: String,
        entry: Map<String, Any>
    ): MutableMap<String, Any> {
        val servers = existing.getOrPut("mcpServers") { mutableMapOf<String, Any>() }
                as? MutableMap<String, Any> ?: mutableMapOf()
        servers[key] = entry
        existing["mcpServers"] = servers
        return existing
    }

    private fun writeMcpConfig(file: File, json: String) {
        FileUtil.createParentDirs(file)
        file.writeText(json)
    }

    private fun showNotification(title: String, content: String, type: NotificationType) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Postman MCP Server")
                ?.createNotification(title, content, type)
                ?.notify(null)
        }
    }

    // Minimal JSON serialization — avoids a heavy dependency for this small config
    private fun formatJson(map: Map<String, Any>, indent: Int = 0): String {
        val pad = "  ".repeat(indent)
        val inner = "  ".repeat(indent + 1)
        val entries = map.entries.joinToString(",\n") { (k, v) ->
            "$inner${jsonString(k)}: ${jsonValue(v, indent + 1)}"
        }
        return "{\n$entries\n$pad}"
    }

    private fun jsonValue(v: Any, indent: Int): String = when (v) {
        is Map<*, *> -> @Suppress("UNCHECKED_CAST") formatJson(v as Map<String, Any>, indent)
        is List<*> -> "[${v.joinToString(", ") { jsonValue(it!!, indent) }}]"
        is String -> jsonString(v)
        is Boolean -> v.toString()
        is Number -> v.toString()
        else -> jsonString(v.toString())
    }

    private fun jsonString(s: String) = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    // Minimal JSON parser for reading existing mcp.json
    @Suppress("UNCHECKED_CAST")
    private fun parseSimpleJson(text: String): MutableMap<String, Any> {
        // Delegate to Gson if available; fall back to empty map on parse failure
        return try {
            val gson = Class.forName("com.google.gson.Gson").getDeclaredConstructor().newInstance()
            val mapType = object : com.google.gson.reflect.TypeToken<MutableMap<String, Any>>() {}.type
            gson.javaClass.getMethod("fromJson", String::class.java, java.lang.reflect.Type::class.java)
                .invoke(gson, text, mapType) as MutableMap<String, Any>
        } catch (e: Exception) {
            mutableMapOf("mcpServers" to mutableMapOf<String, Any>())
        }
    }

    companion object {
        fun getInstance(): PostmanMcpService =
            ApplicationManager.getApplication().getService(PostmanMcpService::class.java)
    }
}

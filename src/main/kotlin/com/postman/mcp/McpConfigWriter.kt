package com.postman.mcp

import java.io.File

/**
 * Pure Kotlin object that handles all MCP config file reading and writing.
 * Has no IntelliJ platform dependencies so it can be tested without a running IDE.
 *
 * Writes a stdio entry that points at the plugin's bundled Node and pre-installed Postman
 * MCP Server (see [BundledRuntime]) — no `npx`, no network at MCP launch time.
 */
internal object McpConfigWriter {

    fun applyConfig(
        apiKey: String,
        nodeBinary: File,
        serverEntry: File,
        targets: List<McpAgentTarget> = McpAgentTarget.ALL,
    ): Result<Unit> = runCatching {
        val entry = buildEntry(apiKey, nodeBinary, serverEntry)
        for (target in targets) {
            val configFile = target.getConfigFile()
            val existing = readExistingConfig(configFile)
            val merged = mergeConfig(existing, "postman", target.adaptEntry(entry), target.rootKey)
            writeConfig(configFile, formatJson(merged))
        }
    }

    fun removeConfig(
        targets: List<McpAgentTarget> = McpAgentTarget.ALL,
    ): Result<Unit> = runCatching {
        for (target in targets) {
            val configFile = target.getConfigFile()
            if (!configFile.exists()) continue
            val existing = readExistingConfig(configFile)
            @Suppress("UNCHECKED_CAST")
            val servers = existing[target.rootKey] as? MutableMap<String, Any>
            if (servers != null) {
                servers.remove("postman")
                if (servers.isEmpty()) existing.remove(target.rootKey)
            }
            writeConfig(configFile, if (existing.isEmpty()) "{}" else formatJson(existing))
        }
    }

    internal fun buildEntry(apiKey: String, nodeBinary: File, serverEntry: File): Map<String, Any> {
        val entry = mutableMapOf<String, Any>(
            "command" to nodeBinary.absolutePath,
            "args" to listOf(serverEntry.absolutePath),
        )
        if (apiKey.isNotBlank()) entry["env"] = mapOf("POSTMAN_API_KEY" to apiKey)
        return entry
    }

    internal fun readExistingConfig(file: File): MutableMap<String, Any> {
        if (!file.exists()) return mutableMapOf()
        return try {
            parseJson(file.readText())
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun mergeConfig(
        existing: MutableMap<String, Any>,
        key: String,
        entry: Map<String, Any>,
        rootKey: String,
    ): MutableMap<String, Any> {
        val servers = existing.getOrPut(rootKey) { mutableMapOf<String, Any>() }
            as? MutableMap<String, Any> ?: mutableMapOf()
        val previous = servers[key] as? Map<String, Any>
        servers[key] = mergeEntry(previous, entry)
        existing[rootKey] = servers
        return existing
    }

    /**
     * Deep-merges the freshly built [fresh] entry into the user's [existing] postman entry so that
     * hand edits survive each [applyConfig] (which runs on every IDE startup):
     *
     *  - `command` and the server-entry arg are always refreshed to the plugin's current bundled
     *    paths (they move when Node / the server version is bumped).
     *  - Extra `args` the user appended after the server entry (e.g. `--full`) are preserved.
     *  - Extra `env` vars (e.g. a region base URL) are preserved; `POSTMAN_API_KEY` is always taken
     *    from [fresh] so the plugin stays the source of truth for the key.
     *
     * If [existing] is null or not a bundled-shaped stdio entry (e.g. a legacy `npx` command or a
     * remote `url` entry from an older plugin version), it is replaced wholesale — there is nothing
     * worth preserving and merging would carry stale args forward.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun mergeEntry(existing: Map<String, Any>?, fresh: Map<String, Any>): Map<String, Any> {
        if (existing == null) return fresh
        val existingArgs = (existing["args"] as? List<*>)?.map { it.toString() } ?: emptyList()
        val command = existing["command"] as? String
        val isBundledShaped = command != null && command != "npx" && existing["url"] == null &&
            existingArgs.firstOrNull()?.endsWith(".js") == true
        if (!isBundledShaped) return fresh

        val merged = LinkedHashMap<String, Any>(existing)
        merged["command"] = fresh.getValue("command")
        val serverEntry = (fresh.getValue("args") as List<*>).first() as String
        merged["args"] = listOf(serverEntry) + existingArgs.drop(1)

        val mergedEnv = LinkedHashMap<String, Any>()
        (existing["env"] as? Map<*, *>)?.forEach { (k, v) -> mergedEnv[k.toString()] = v as Any }
        (fresh["env"] as? Map<*, *>)?.forEach { (k, v) -> mergedEnv[k.toString()] = v as Any }
        if (mergedEnv.isEmpty()) merged.remove("env") else merged["env"] = mergedEnv
        return merged
    }

    private fun writeConfig(file: File, json: String) {
        file.parentFile?.mkdirs()
        file.writeText(json)
    }

    internal fun formatJson(map: Map<String, Any>, indent: Int = 0): String {
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

    @Suppress("UNCHECKED_CAST")
    internal fun parseJson(text: String): MutableMap<String, Any> {
        return try {
            val gson = Class.forName("com.google.gson.Gson").getDeclaredConstructor().newInstance()
            val mapType = object : com.google.gson.reflect.TypeToken<MutableMap<String, Any>>() {}.type
            gson.javaClass.getMethod("fromJson", String::class.java, java.lang.reflect.Type::class.java)
                .invoke(gson, text, mapType) as MutableMap<String, Any>
        } catch (_: Exception) {
            mutableMapOf()
        }
    }
}

package com.postman.mcp

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.Decompressor
import com.intellij.util.io.HttpRequests
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Bundles a self-contained Node.js runtime and a pinned Postman MCP Server install under the
 * plugin's IDE config directory, so end users never need to install Node themselves.
 *
 * - Node is downloaded from nodejs.org on first setup and SHA-256 verified.
 * - The pinned MCP server version is installed via the bundled npm into the same cache.
 * - Both are cached across IDE restarts; only a plugin version bump that changes the pinned
 *   versions triggers a re-download.
 *
 * Pure object (no IntelliJ services). Heavy operations take a [ProgressIndicator] so the
 * caller can drive a modal progress UI and support cancellation.
 */
internal object BundledRuntime {

    const val NODE_VERSION = "24.16.0"
    const val MCP_SERVER_VERSION = "2.9.0"

    private const val NODE_DIST = "https://nodejs.org/dist"

    data class Paths(val node: File, val serverEntry: File)

    fun cacheRoot(): File {
        val override = System.getProperty("com.postman.mcp.test.cacheRoot")
        return if (override != null) File(override)
        else File(PathManager.getConfigPath(), "postman-mcp-server")
    }

    private fun nodeHome() = File(cacheRoot(), "node/v$NODE_VERSION")
    private fun serverHome() = File(cacheRoot(), "server/v$MCP_SERVER_VERSION")

    private val isWindows get() = SystemInfo.isWindows

    fun nodeBin(): File =
        if (isWindows) File(nodeHome(), "node.exe") else File(nodeHome(), "bin/node")

    private fun npmCliJs(): File =
        if (isWindows) File(nodeHome(), "node_modules/npm/bin/npm-cli.js")
        else File(nodeHome(), "lib/node_modules/npm/bin/npm-cli.js")

    fun serverEntry(): File =
        File(serverHome(), "node_modules/@postman/postman-mcp-server/dist/src/index.js")

    /** Returns paths if both Node and the server are present on disk; null otherwise. No I/O beyond stat. */
    fun cachedPaths(): Paths? {
        val n = nodeBin()
        val s = serverEntry()
        return if (n.isFile && s.isFile) Paths(n, s) else null
    }

    /**
     * Ensures Node and the MCP server are installed locally, downloading if needed. Blocking.
     * Re-runs are cheap when the cache is intact.
     */
    fun ensure(indicator: ProgressIndicator? = null): Result<Paths> = runCatching {
        ensureNode(indicator)
        ensureServer(indicator)
        Paths(nodeBin(), serverEntry())
    }

    // ── Node download ──────────────────────────────────────────────────────────

    private fun ensureNode(indicator: ProgressIndicator?) {
        if (nodeBin().isFile) return
        val archiveName = nodeArchiveName()
        val archiveUrl = "$NODE_DIST/v$NODE_VERSION/$archiveName"
        val expectedSha = fetchSha256(archiveName)

        indicator?.text = "Downloading Node.js v$NODE_VERSION (~30 MB)…"
        indicator?.isIndeterminate = false
        val tmp = File.createTempFile("postman-mcp-node-", ".archive")
        try {
            HttpRequests.request(archiveUrl)
                .productNameAsUserAgent()
                .saveToFile(tmp.toPath(), indicator)
            indicator?.checkCanceled()
            indicator?.text = "Verifying Node.js download…"
            verifySha256(tmp, expectedSha)
            indicator?.text = "Extracting Node.js…"
            installArchive(tmp, archiveName, nodeHome())
            if (!isWindows) nodeBin().setExecutable(true, false)
        } finally {
            tmp.delete()
        }
    }

    private fun nodeArchiveName(): String {
        val isArm = SystemInfo.OS_ARCH.let { it.contains("aarch64") || it.contains("arm64") }
        val platform = when {
            SystemInfo.isWindows -> if (isArm) "win-arm64" else "win-x64"
            SystemInfo.isMac -> if (isArm) "darwin-arm64" else "darwin-x64"
            SystemInfo.isLinux -> if (isArm) "linux-arm64" else "linux-x64"
            else -> error("Unsupported OS: ${SystemInfo.OS_NAME}")
        }
        val ext = if (SystemInfo.isWindows) "zip" else "tar.gz"
        return "node-v$NODE_VERSION-$platform.$ext"
    }

    private fun fetchSha256(filename: String): String {
        val sums = HttpRequests.request("$NODE_DIST/v$NODE_VERSION/SHASUMS256.txt")
            .productNameAsUserAgent()
            .readString()
        val line = sums.lineSequence().firstOrNull { it.trim().endsWith(" $filename") }
            ?: error("SHASUMS256.txt missing entry for $filename")
        return line.substringBefore(' ').trim()
    }

    private fun verifySha256(file: File, expectedHex: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
        require(actualHex.equals(expectedHex, ignoreCase = true)) {
            "Node download checksum mismatch: expected $expectedHex, got $actualHex"
        }
    }

    private fun installArchive(archive: File, archiveName: String, target: File) {
        cacheRoot().mkdirs()
        val staging = File(cacheRoot(), ".staging-${System.nanoTime()}")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            val path = archive.toPath()
            val stagingPath = staging.toPath()
            if (archiveName.endsWith(".zip")) {
                Decompressor.Zip(path).extract(stagingPath)
            } else {
                Decompressor.Tar(path).extract(stagingPath)
            }
            val inner = staging.listFiles()?.singleOrNull { it.isDirectory }
                ?: error("Unexpected Node archive layout under ${staging.absolutePath}")
            target.parentFile.mkdirs()
            target.deleteRecursively()
            try {
                Files.move(inner.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(inner.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    // ── MCP server install ─────────────────────────────────────────────────────

    private fun ensureServer(indicator: ProgressIndicator?) {
        if (serverEntry().isFile) return
        require(nodeBin().isFile) { "Node must be installed before the MCP server" }
        indicator?.text = "Installing Postman MCP Server v$MCP_SERVER_VERSION…"
        indicator?.isIndeterminate = true
        val home = serverHome()
        home.deleteRecursively()
        home.mkdirs()
        // Pre-seed package.json so npm treats this dir as a self-contained project.
        File(home, "package.json").writeText(
            """{"name":"postman-mcp-runtime","version":"0.0.0","private":true}"""
        )
        runNpmInstall(home, "@postman/postman-mcp-server@$MCP_SERVER_VERSION", indicator)
        require(serverEntry().isFile) {
            "npm install completed but server entry not found at ${serverEntry().absolutePath}"
        }
    }

    private fun runNpmInstall(workdir: File, spec: String, indicator: ProgressIndicator?) {
        val cmd = listOf(
            nodeBin().absolutePath,
            npmCliJs().absolutePath,
            "install",
            "--no-audit",
            "--no-fund",
            "--loglevel=error",
            "--prefix", workdir.absolutePath,
            spec,
        )
        val proc = ProcessBuilder(cmd)
            .directory(workdir)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        proc.inputStream.bufferedReader().use { reader ->
            reader.lineSequence().forEach { line ->
                output.appendLine(line)
                indicator?.checkCanceled()
            }
        }
        val exit = proc.waitFor()
        require(exit == 0) { "npm install failed (exit $exit):\n$output" }
    }
}

package com.postman.mcp

import java.io.File

/** Synthetic Node + server paths used by tests; real bootstrap code is not exercised here. */
internal val FAKE_NODE: File = File("/fake/node/bin/node")
internal val FAKE_SERVER: File = File("/fake/server/index.js")

/** Convenience for tests that don't care about the bundled-runtime paths. */
internal fun McpConfigWriter.applyConfig(apiKey: String): Result<Unit> =
    applyConfig(apiKey, FAKE_NODE, FAKE_SERVER)

# Postman MCP Server — JetBrains Plugin

Connect JetBrains AI Assistant to your Postman workspace via the [Postman MCP Server](https://github.com/postmanlabs/postman-mcp-server).

## What it does

After installing and configuring the plugin, JetBrains AI Assistant can:

- Manage Postman collections, workspaces, and environments
- Run API tests and generate client code
- Search public and private API definitions
- Create and update Postman specs from code

## Requirements

- IntelliJ IDEA 2025.1 or later
- JetBrains AI Assistant plugin enabled
- Node.js (for local stdio mode only)

## Installation

Install from the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/com.postman.mcp-server).

## Configuration

1. Go to **Settings → Tools → Postman MCP Server**
2. Choose a connection type:
   - **Remote OAuth** — no API key needed, US region only (recommended)
   - **Remote API key** — use your [Postman API key](https://postman.postman.co/settings/me/api-keys), supports US and EU
   - **Local stdio** — runs `npx @postman/postman-mcp-server@latest` locally
3. Choose a tool set:
   - **Minimal** — essential tools, fastest performance (default)
   - **Full** — all 100+ Postman tools
   - **Code** — API search and code generation tools
4. Click **Write MCP config now** (or **Apply**)
5. Restart your IDE to activate

The plugin writes the MCP server config to JetBrains AI Assistant's `mcp.json` settings file.

## Building from source

```bash
./gradlew buildPlugin
```

The plugin ZIP is output to `build/distributions/`.

## License

Apache 2.0 — see [LICENSE](LICENSE)

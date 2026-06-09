# Postman MCP Server — JetBrains Plugin

Connect any AI agent in your JetBrains IDE to your Postman workspace via the [Postman MCP Server](https://github.com/postmanlabs/postman-mcp-server).

A single configuration covers every JetBrains AI agent that reads MCP — AI Assistant, Junie, Codex, Claude, GitHub Copilot for JetBrains, and any future addition — because they all share `~/.ai/mcp/mcp.json`.

## What your AI agent can do once connected

- Manage Postman collections, workspaces, and environments
- Run API tests and generate client code
- Search public and private API definitions
- Create and update Postman specs from code

## Requirements

- IntelliJ IDEA (or any JetBrains IDE) 2025.1 or later
- At least one supported AI agent plugin installed (AI Assistant, Junie, GitHub Copilot, …)
- [Node.js](https://nodejs.org/) on your `PATH` — the MCP server runs locally via `npx`

## Installation

Install from the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/31578-postman-mcp-server).

## First-time setup

1. Open any project after installing the plugin.
2. A **Connect to Postman** dialog appears. Paste your [Postman API key](https://go.postman.co/settings/me/api-keys) and click **Connect**.
3. Accept the IDE's prompt to enable the MCP server when it appears.

That's it — no manual config files, no restart. The plugin writes the stdio MCP entry (`npx -y @postman/postman-mcp-server@latest`) to `~/.ai/mcp/mcp.json`, and on every IDE startup it re-applies the entry so the file stays current.

## Updating your API key

**Tools → Update Postman API Key**

## Building from source

```bash
./gradlew buildPlugin
```

The plugin ZIP is output to `build/distributions/`.

## License

Apache 2.0 — see [LICENSE](LICENSE)

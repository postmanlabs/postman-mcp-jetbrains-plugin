package com.postman.mcp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*

@State(
    name = "PostmanMcpSettings",
    storages = [Storage("postmanMcpSettings.xml")]
)
@Service(Service.Level.APP)
class PostmanMcpSettings : PersistentStateComponent<PostmanMcpSettings.State> {

    data class State(
        var connectionType: ConnectionType = ConnectionType.REMOTE_OAUTH,
        var toolSet: ToolSet = ToolSet.MINIMAL,
        var apiKey: String = "",
        var region: Region = Region.US,
        var configured: Boolean = false
    )

    enum class ConnectionType {
        LOCAL_STDIO,
        REMOTE_API_KEY,
        REMOTE_OAUTH
    }

    enum class ToolSet(val flag: String, val remotePath: String) {
        MINIMAL("", "minimal"),
        FULL("--full", "mcp"),
        CODE("--code", "code")
    }

    enum class Region(val baseUrl: String) {
        US("https://mcp.postman.com"),
        EU("https://mcp.eu.postman.com")
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): PostmanMcpSettings =
            ApplicationManager.getApplication().getService(PostmanMcpSettings::class.java)
    }
}

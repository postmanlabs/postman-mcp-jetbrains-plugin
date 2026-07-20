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
        var configured: Boolean = false,
        /** Opt-in usage telemetry consent (PSTAPI-1497). Off by default; set only on explicit consent. */
        var telemetryEnabled: Boolean = false,
    )

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

package com.freetime.maic.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.freetime.maic.settings.AppSettingsState",
    storages = [Storage("MultiAiChatSettings.xml")]
)
class AppSettingsState : PersistentStateComponent<AppSettingsState> {
    var openAiKey: String = ""
    var anthropicKey: String = ""
    var geminiKey: String = ""
    var selectedProvider: String = "OpenAI"
    var lastVersion: String = ""

    var openAiModel: String = "gpt-4o"
    var anthropicModel: String = "claude-3-5-sonnet-20240620"
    var geminiModel: String = "gemini-3.6-flash"

    override fun getState(): AppSettingsState = this

    override fun loadState(state: AppSettingsState) {
        XmlSerializerUtil.copyBean(state, this)

        // Migrate deprecated Gemini 1.5 models saved by older plugin versions.
        if (geminiModel.startsWith("gemini-1.5")) {
            geminiModel = "gemini-2.5-flash"
        }
        if (geminiModel.startsWith("gemini-2.5")) {
            geminiModel = "gemini-3.6-flash"
        }
    }

    companion object {
        val instance: AppSettingsState
            get() = ApplicationManager.getApplication().getService(AppSettingsState::class.java)
    }
}

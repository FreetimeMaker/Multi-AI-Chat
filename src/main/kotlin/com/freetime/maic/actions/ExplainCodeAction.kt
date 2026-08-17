package com.freetime.maic.actions

import com.freetime.maic.api.AiClientFactory
import com.freetime.maic.services.ChatService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExplainCodeAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText ?: return
        val chatService = project.service<ChatService>()
        val userName = System.getProperty("user.name") ?: "User"

        if (selectedText.isNotBlank()) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Multi AI Chat")
            toolWindow?.show()

            CoroutineScope(Dispatchers.IO).launch {

                // User message
                chatService.emitDelta(userName, selectedText, true)

                // System message
                chatService.emitDelta("System", "Explaining code...", true)

                val prompt = "Please explain this code in detail:\n\n```\n$selectedText\n```"

                // Start AI message
                chatService.emitDelta("AI", "", true)

                AiClientFactory.getClient().generateResponseStream(prompt).collect { chunk ->
                    chatService.emitDelta("AI", chunk, false)
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() ?: false
    }
}

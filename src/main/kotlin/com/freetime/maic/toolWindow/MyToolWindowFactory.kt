package com.freetime.maic.toolWindow

import com.freetime.maic.api.AiClientFactory
import com.freetime.maic.services.ChatService
import com.freetime.maic.settings.AppSettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import com.intellij.openapi.ui.ComboBox
import kotlinx.coroutines.*
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextField

class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindowContent(project)
        val content = ContentFactory.getInstance().createContent(myToolWindow.contentPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private class MyToolWindowContent(val project: Project) {
        val contentPanel = JPanel(BorderLayout())
        private val chatArea = JBTextArea()
        private val inputField = JTextField()
        private val sendButton = JButton("Send")
        private val providerBox = ComboBox(arrayOf("OpenAI", "Gemini", "Anthropic"))
        private val modelBox = ComboBox<String>()
        private val clearButton = JButton("Clear")
        private val chatService = project.service<ChatService>()
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        private val providerModels = mapOf(
            "OpenAI" to arrayOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo"),
            "Gemini" to arrayOf("gemini-3.6-flash", "gemini-3.6-pro"),
            "Anthropic" to arrayOf("claude-3-5-sonnet-20240620", "claude-3-opus-20240229", "claude-3-haiku-20240307")
        )

        init {
            val topPanel = JPanel(BorderLayout())
            val comboPanel = JPanel(BorderLayout())
            
            providerBox.selectedItem = AppSettingsState.instance.selectedProvider
            updateModelBox(AppSettingsState.instance.selectedProvider)
            
            providerBox.addActionListener { 
                val provider = providerBox.selectedItem as String
                AppSettingsState.instance.selectedProvider = provider
                updateModelBox(provider)
            }

            modelBox.addActionListener {
                val model = modelBox.selectedItem as? String ?: return@addActionListener
                val settings = AppSettingsState.instance
                when (settings.selectedProvider) {
                    "OpenAI" -> settings.openAiModel = model
                    "Gemini" -> settings.geminiModel = model
                    "Anthropic" -> settings.anthropicModel = model
                }
            }

            comboPanel.add(providerBox, BorderLayout.WEST)
            comboPanel.add(modelBox, BorderLayout.CENTER)
            
            topPanel.add(comboPanel, BorderLayout.CENTER)
            topPanel.add(clearButton, BorderLayout.EAST)
            
            chatArea.isEditable = false
            chatArea.lineWrap = true
            chatArea.wrapStyleWord = true
            
            val inputPanel = JPanel(BorderLayout())
            inputPanel.add(inputField, BorderLayout.CENTER)
            inputPanel.add(sendButton, BorderLayout.EAST)

            contentPanel.add(topPanel, BorderLayout.NORTH)
            contentPanel.add(JBScrollPane(chatArea), BorderLayout.CENTER)
            contentPanel.add(inputPanel, BorderLayout.SOUTH)

            sendButton.addActionListener { handleUserInput() }
            inputField.addActionListener { handleUserInput() }
            clearButton.addActionListener { chatArea.text = "" }

            scope.launch {
                chatService.messages.collect { delta ->
                    if (delta.isNewMessage) {
                        chatArea.append("\n${delta.sender}: ${delta.text}")
                    } else {
                        chatArea.append(delta.text)
                    }
                    chatArea.caretPosition = chatArea.document.length
                }
            }
        }

        private fun updateModelBox(provider: String) {
            modelBox.removeAllItems()
            providerModels[provider]?.forEach { modelBox.addItem(it) }
            
            val settings = AppSettingsState.instance
            val selectedModel = when (provider) {
                "OpenAI" -> settings.openAiModel
                "Gemini" -> settings.geminiModel
                "Anthropic" -> settings.anthropicModel
                else -> ""
            }
            modelBox.selectedItem = selectedModel
        }

        private fun handleUserInput() {
            val text = inputField.text
            if (text.isNotBlank()) {
                inputField.text = ""
                scope.launch {
                    chatService.emitDelta("You", text, true)
                    chatService.emitDelta("AI", "", true)
                    
                    withContext(Dispatchers.IO) {
                        AiClientFactory.getClient().generateResponseStream(text).collect { chunk ->
                            chatService.emitDelta("AI", chunk, false)
                        }
                    }
                }
            }
        }
    }
}

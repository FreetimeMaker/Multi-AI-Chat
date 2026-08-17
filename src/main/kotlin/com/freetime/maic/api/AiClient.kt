package com.freetime.maic.api

import com.google.gson.Gson
import com.freetime.maic.settings.AppSettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

interface AiClient {
    fun generateResponseStream(prompt: String): Flow<String>
}

class OpenAIClient(private val apiKey: String) : AiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        if (apiKey.isBlank()) {
            emit("Error: No API key provided.")
            return@flow
        }

        val requestBody = mapOf(
            "model" to "gpt-3.5-turbo",
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "stream" to true
        )

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            emit("Error: ${response.code}")
            response.close()
            return@flow
        }

        response.body?.source()?.let { source ->
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.substring(6)
                    if (data == "[DONE]") break
                    try {
                        val chunk = gson.fromJson(data, OpenAiChunk::class.java)
                        chunk.choices.firstOrNull()?.delta?.content?.let {
                            emit(it)
                        }
                    } catch (e: Exception) {}
                }
            }
        }
        response.close()
    }

    private data class OpenAiChunk(val choices: List<Choice>)
    private data class Choice(val delta: Delta)
    private data class Delta(val content: String?)
}

class AnthropicClient(val key: String) : AiClient { 
    override fun generateResponseStream(prompt: String) = flow { emit("Anthropic streaming coming soon...") } 
}

class GeminiClient(val key: String) : AiClient { 
    override fun generateResponseStream(prompt: String) = flow { emit("Gemini streaming coming soon...") } 
}

object AiClientFactory {
    fun getClient(): AiClient {
        val settings = AppSettingsState.instance
        return when (settings.selectedProvider) {
            "Anthropic" -> AnthropicClient(settings.anthropicKey)
            "Gemini" -> GeminiClient(settings.geminiKey)
            else -> OpenAIClient(settings.openAiKey)
        }
    }
}

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

class OpenAIClient(private val apiKey: String, private val model: String) : AiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        if (apiKey.isBlank()) {
            emit("Error: No OpenAI API key provided. Please check settings.")
            return@flow
        }

        val requestBody = mapOf(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "stream" to true
        )

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emit("Error: ${response.code} - ${response.message}")
                response.close()
                return@flow
            }

            response.body?.source()?.let { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6).trim()
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
        } catch (e: Exception) {
            emit("Connection Error: ${e.message}")
        }
    }

    private data class OpenAiChunk(val choices: List<Choice>)
    private data class Choice(val delta: Delta)
    private data class Delta(val content: String?)
}

class AnthropicClient(private val apiKey: String, private val model: String) : AiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        if (apiKey.isBlank()) {
            emit("Error: No Anthropic API key provided. Please check settings.")
            return@flow
        }

        val requestBody = mapOf(
            "model" to model,
            "max_tokens" to 4096,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "stream" to true
        )

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emit("Error: ${response.code} - ${response.message}")
                response.close()
                return@flow
            }

            response.body?.source()?.let { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6).trim()
                        try {
                            val json = gson.fromJson(data, AnthropicChunk::class.java)
                            if (json.type == "content_block_delta") {
                                json.delta?.text?.let { emit(it) }
                            }
                        } catch (e: Exception) {}
                    }
                }
            }
            response.close()
        } catch (e: Exception) {
            emit("Connection Error: ${e.message}")
        }
    }

    private data class AnthropicChunk(val type: String, val delta: AnthropicDelta?)
    private data class AnthropicDelta(val text: String?)
}

class GeminiClient(private val apiKey: String, private val model: String) : AiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        if (apiKey.isBlank()) {
            emit("Error: No Gemini API key provided. Please check settings.")
            return@flow
        }

        val requestBody = mapOf(
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to prompt)))
            )
        )

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?key=$apiKey")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emit("Error: ${response.code} - ${response.message}")
                response.close()
                return@flow
            }

            response.body?.source()?.let { source ->
                // Gemini returns a stream of JSON objects, usually wrapped in [ ... ] for streamGenerateContent
                // but OkHttp's source.readUtf8Line() might not be ideal if it's one big JSON array.
                // However, streamGenerateContent often uses a format where each chunk is a JSON object in a list.
                // A safer way is to read the entire body if it's small, but for streaming we need to parse.
                
                val content = source.readUtf8()
                // Simple parsing for Gemini's specific stream format which is a JSON array
                try {
                    val chunks = gson.fromJson(content, Array<GeminiResponse>::class.java)
                    chunks.forEach { chunk ->
                        chunk.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.let {
                            emit(it)
                        }
                    }
                } catch (e: Exception) {
                    // Fallback for non-array format if applicable
                    try {
                        val chunk = gson.fromJson(content, GeminiResponse::class.java)
                        chunk.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.let {
                            emit(it)
                        }
                    } catch (e: Exception) {
                        emit("Error parsing Gemini response")
                    }
                }
            }
            response.close()
        } catch (e: Exception) {
            emit("Connection Error: ${e.message}")
        }
    }

    private data class GeminiResponse(val candidates: List<GeminiCandidate>)
    private data class GeminiCandidate(val content: GeminiContent)
    private data class GeminiContent(val parts: List<GeminiPart>)
    private data class GeminiPart(val text: String?)
}

object AiClientFactory {
    fun getClient(): AiClient {
        val settings = AppSettingsState.instance
        return when (settings.selectedProvider) {
            "Anthropic" -> AnthropicClient(settings.anthropicKey, settings.anthropicModel)
            "Gemini" -> GeminiClient(settings.geminiKey, settings.geminiModel)
            else -> OpenAIClient(settings.openAiKey, settings.openAiModel)
        }
    }
}

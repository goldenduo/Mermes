package com.mermes.model

import com.google.gson.Gson
import com.mermes.common.log.MermesLog as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ModelDiscoveryServiceImpl : ModelDiscoveryService {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "ModelDiscoveryServiceImpl"
        
        @Volatile
        private var instance: ModelDiscoveryServiceImpl? = null

        fun getInstance(): ModelDiscoveryServiceImpl {
            return instance ?: synchronized(this) {
                instance ?: ModelDiscoveryServiceImpl().also { instance = it }
            }
        }
    }

    override suspend fun discoverModels(
        provider: String,
        baseUrl: String,
        apiKey: String?
    ): List<String> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val providerLower = provider.trim().lowercase()

        // 1. Predefined offline fallbacks to guarantee robust UI suggestions
        val offlineFallbacks = when (providerLower) {
            "ollama" -> listOf("llama3:8b", "qwen2.5:7b", "deepseek-r1:8b", "mistral:latest", "phi3:latest")
            "deepseek" -> listOf("deepseek-chat", "deepseek-coder", "deepseek-reasoner")
            "openrouter" -> listOf("deepseek/deepseek-chat", "google/gemini-pro-1.5", "anthropic/claude-3.5-sonnet", "meta-llama/llama-3-70b-instruct")
            else -> listOf("gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo", "text-embedding-3-small")
        }

        if (cleanUrl.isEmpty()) {
            return@withContext offlineFallbacks
        }

        // 2. Perform online discovery
        try {
            val url = if (providerLower == "ollama") {
                "$cleanUrl/api/tags"
            } else {
                "$cleanUrl/v1/models"
            }

            val requestBuilder = Request.Builder().url(url)
            if (!apiKey.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Server responded with error code ${response.code} for URL: $url")
                    return@withContext offlineFallbacks
                }

                val bodyString = response.body?.string() ?: return@withContext offlineFallbacks
                
                return@withContext if (providerLower == "ollama") {
                    // Ollama parses: models[*].name
                    val map = gson.fromJson(bodyString, Map::class.java)
                    val modelsList = map["models"] as? List<*> ?: return@withContext offlineFallbacks
                    modelsList.mapNotNull { modelObj ->
                        val modelMap = modelObj as? Map<*, *>
                        modelMap?.get("name")?.toString() ?: modelMap?.get("model")?.toString()
                    }
                } else {
                    // OpenAI style parses: data[*].id
                    val map = gson.fromJson(bodyString, Map::class.java)
                    val dataList = map["data"] as? List<*> ?: return@withContext offlineFallbacks
                    dataList.mapNotNull { dataObj ->
                        val dataMap = dataObj as? Map<*, *>
                        dataMap?.get("id")?.toString()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network discovery failed for provider $provider at $cleanUrl. Falling back to presets.", e)
            return@withContext offlineFallbacks
        }
    }
}

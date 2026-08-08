package com.aikb.ime.ai

import android.util.Log
import com.aikb.ime.util.Preferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.*

/** 云端 AI 客户端，支持 OpenAI 兼容接口 */
object AIClient {
    private const val TAG = "AIClient"
    private val executor: ExecutorService = Executors.newFixedThreadPool(2)

    fun generate(
        systemPrompt: String,
        userMessage: String,
        temperature: Float = 0.8f,
        callback: (String) -> Unit
    ) {
        executor.submit {
            try {
                val result = callApi(systemPrompt, userMessage, temperature)
                callback(result)
            } catch (e: Exception) {
                Log.e(TAG, "AI调用失败", e)
                callback("[网络错误: ${e.message}]")
            }
        }
    }

    private fun callApi(systemPrompt: String, userMessage: String, temperature: Float): String {
        val apiUrl = Preferences.apiUrl
        val apiKey = Preferences.apiKey
        val model = Preferences.model

        if (apiKey.isBlank()) return "[请先在设置中配置 API Key]"

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userMessage))
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", temperature)
            put("max_tokens", 300)
        }.toString()

        // 兼容各种 API 服务商的 URL 格式
        // 用户可填: https://api.openai.com/v1 或 https://api.openai.com
        // 智谱: https://open.bigmodel.cn/api/paas/v4
        // 字节: https://ark.cn-beijing.volces.com/api/v3
        val endpoint: String = when {
            apiUrl.endsWith("/chat/completions") -> apiUrl
            apiUrl.endsWith("/v4") -> apiUrl + "/chat/completions"
            apiUrl.endsWith("/v3") -> apiUrl + "/chat/completions"
            apiUrl.endsWith("/v1") -> apiUrl + "/chat/completions"
            apiUrl.endsWith("/") -> apiUrl + "v1/chat/completions"
            else -> apiUrl + "/v1/chat/completions"
        }

        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 30000

        try {
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }

            val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }

            val json = JSONObject(response.toString())
            val choices = json.getJSONArray("choices")
            if (choices.length() > 0) {
                return choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            }
            return "[AI返回为空]"
        } catch (e: Exception) {
            try {
                val errReader = BufferedReader(InputStreamReader(conn.errorStream, "UTF-8"))
                val errText = StringBuilder()
                var errLine: String?
                while (errReader.readLine().also { errLine = it } != null) {
                    errText.append(errLine)
                }
                return "[API错误: ${errText.toString().take(100)}]"
            } catch (_: Exception) {
                return "[网络错误: ${e.message}]"
            }
        } finally {
            conn.disconnect()
        }
    }
}
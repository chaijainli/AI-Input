package com.aikb.ime.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
                if (!isNetworkAvailable()) {
                    callback("[网络错误: 当前无网络连接]")
                    return@submit
                }
                val result = callApi(systemPrompt, userMessage, temperature)
                callback(result)
            } catch (e: Exception) {
                Log.e(TAG, "AI调用失败", e)
                callback("[网络错误: ${e.message}]")
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        try {
            val cm = (Preferences.context?.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager)
            val net = cm.activeNetwork ?: return false
            val cap = cm.getNetworkCapabilities(net) ?: return false
            return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            return false
        }
    }

    private fun callApi(systemPrompt: String, userMessage: String, temperature: Float): String {
        val apiUrl = Preferences.apiUrl
        val apiKey = Preferences.apiKey
        val endpoint: String = when {
            apiUrl.endsWith("/chat/completions") -> apiUrl
            apiUrl.endsWith("/v4") -> apiUrl + "/chat/completions"
            apiUrl.endsWith("/v3") -> apiUrl + "/chat/completions"
            apiUrl.endsWith("/v1") -> apiUrl + "/chat/completions"
            apiUrl.endsWith("/") -> apiUrl + "v1/chat/completions"
            else -> apiUrl + "/v1/chat/completions"
        }

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userMessage))
        }

        if (apiKey.isBlank()) return "[请先在设置中配置 API Key]"

        for (model in Preferences.modelCandidates) {
            val result = callApiWithModel(apiKey, messages, model, temperature, endpoint)
            when {
                result.startsWith("[模型不存在]") -> {
                    Log.w(TAG, "模型 $model 不存在，尝试下一个")
                    continue
                }
                result.startsWith("[API错误]") || result.startsWith("[网络错误]") ||
                    result.startsWith("[AI返回为空]") -> return result
                else -> return result
            }
        }
        return "[API错误: 所有预置模型均不可用]"
    }

    private fun callApiWithModel(apiKey: String, messages: JSONArray, model: String, temperature: Float, endpoint: String): String {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", temperature)
            put("max_tokens", 300)
        }.toString()

        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 15000

        try {
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }

            val responseCode = conn.responseCode

            // 非 2xx 响应码 → 从 errorStream 读取错误详情
            if (responseCode != 200) {
                try {
                    val errReader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream, "UTF-8"))
                    val errText = StringBuilder()
                    var line: String?
                    while (errReader.readLine().also { line = it } != null) errText.append(line)
                    val errStr = errText.toString()

                    // 判断是否为模型不存在
                    if (responseCode == 400 || responseCode == 404 ||
                        errStr.contains("model", ignoreCase = true) ||
                        errStr.contains("not found", ignoreCase = true)) {
                        return "[模型不存在: $model]"
                    }
                    return "[API错误: ${errStr.take(100)}]"
                } catch (_: Exception) {
                    return "[模型不存在: $model]"
                }
            }

            // 200 OK → 正常解析响应
            val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) response.append(line)

            val json = JSONObject(response.toString())
            val choices = json.getJSONArray("choices")
            if (choices.length() > 0) {
                return choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            }

            try {
                val errType = json.getJSONObject("error").optString("type", "")
                val errMsg = json.getJSONObject("error").optString("message", "")
                if (errType.contains("model", ignoreCase = true) || errMsg.contains("model", ignoreCase = true)) {
                    return "[模型不存在: $model]"
                }
            } catch (_: Exception) {}

            return "[模型不存在: $model]"
        } catch (e: Exception) {
            return "[网络错误: ${e.message}]"
        } finally {
            conn.disconnect()
        }
    }
}
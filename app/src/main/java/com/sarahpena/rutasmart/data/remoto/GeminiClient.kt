package com.sarahpena.rutasmart.data.remoto

import com.sarahpena.rutasmart.domain.ia.AiConfig
import com.sarahpena.rutasmart.domain.ia.AiErrorMapper
import com.sarahpena.rutasmart.domain.ia.Turn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Cliente HTTP de Google Gemini (API generateContent).
 *  - Solo HTTPS.
 *  - La clave viaja en el encabezado x-goog-api-key (nunca en la URL).
 *  - La clave NO está en el código: se lee de local.properties (GEMINI_API_KEY) y llega por BuildConfig.
 */
class GeminiClient(private val apiKey: String, private val model: String = AiConfig.MODEL) {

    sealed class Result {
        data class Ok(val text: String) : Result()
        data class Fail(val reason: String) : Result()
    }

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    suspend fun generate(systemInstruction: String, history: List<Turn>, userText: String): Result =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext Result.Fail(AiErrorMapper.NOT_CONFIGURED)
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(AiConfig.endpoint(model)).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = AiConfig.TIMEOUT_MS
                    readTimeout = AiConfig.TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("x-goog-api-key", apiKey)
                }
                val body = buildBody(systemInstruction, history + Turn(Turn.USER, userText))
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code !in 200..299) return@withContext Result.Fail(AiErrorMapper.fromHttp(code))
                val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parse(raw)
            } catch (e: SocketTimeoutException) {
                Result.Fail(AiErrorMapper.TIMEOUT)
            } catch (e: UnknownHostException) {
                Result.Fail(AiErrorMapper.NO_INTERNET)
            } catch (e: IOException) {
                Result.Fail(AiErrorMapper.NO_INTERNET)
            } catch (e: JSONException) {
                Result.Fail(AiErrorMapper.BAD_RESPONSE)
            } finally {
                conn?.disconnect()
            }
        }

    private fun buildBody(system: String, turns: List<Turn>): JSONObject {
        val contents = JSONArray()
        turns.forEach { t ->
            contents.put(
                JSONObject()
                    .put("role", t.role)
                    .put("parts", JSONArray().put(JSONObject().put("text", t.text)))
            )
        }
        return JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.4).put("maxOutputTokens", 400))
    }

    private fun parse(raw: String): Result {
        val json = JSONObject(raw)
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            return if (json.optJSONObject("promptFeedback")?.has("blockReason") == true)
                Result.Fail(AiErrorMapper.BLOCKED) else Result.Fail(AiErrorMapper.BAD_RESPONSE)
        }
        val first = candidates.getJSONObject(0)
        if (first.optString("finishReason") == "SAFETY") return Result.Fail(AiErrorMapper.BLOCKED)
        val parts = first.optJSONObject("content")?.optJSONArray("parts") ?: return Result.Fail(AiErrorMapper.BAD_RESPONSE)
        val text = buildString {
            for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
        }
        return if (text.isBlank()) Result.Fail(AiErrorMapper.BAD_RESPONSE) else Result.Ok(text)
    }
}

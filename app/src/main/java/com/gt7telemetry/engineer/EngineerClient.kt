package com.gt7telemetry.engineer

import com.gt7telemetry.settings.EngineerProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The built-in race engineer's API client. Deliberately minimal to make
 * runaway cost impossible:
 *
 *  - exactly ONE request per button press — no agentic loop, no retries,
 *    no follow-up calls;
 *  - the response is hard-capped at [MAX_OUTPUT_TOKENS];
 *  - the briefing itself is bounded by [Briefing.MAX_TRACE_ROWS].
 *
 * One analysis is therefore a few thousand input tokens + ≤1500 output
 * tokens — a few cents on Claude, free within Gemini's free tier. The key
 * is the user's own, entered in Settings and stored only on-device.
 */
object EngineerClient {

    // Claude 5-family models think by default, and max_tokens caps
    // thinking + visible text TOGETHER — a tight cap gets eaten by the
    // thinking and yields an empty report. 4096 leaves room for both while
    // still bounding a single analysis to a few cents.
    private const val MAX_OUTPUT_TOKENS = 4096
    private const val TIMEOUT_MS = 120_000

    private val json = Json { ignoreUnknownKeys = true }

    /** Blocking — call from Dispatchers.IO. Returns the engineer's reply or a failure. */
    fun ask(provider: EngineerProvider, apiKey: String, model: String, briefing: String): Result<String> {
        val useModel = model.ifBlank { provider.defaultModel }
        return runCatching {
            when (provider) {
                EngineerProvider.ANTHROPIC -> askAnthropic(apiKey, useModel, briefing)
                EngineerProvider.GEMINI -> askGemini(apiKey, useModel, briefing)
            }
        }
    }

    private fun askAnthropic(apiKey: String, model: String, briefing: String): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", MAX_OUTPUT_TOKENS)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", briefing)
                })
            }
        }.toString()

        val resp = post(
            url = "https://api.anthropic.com/v1/messages",
            body = body,
            headers = mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01",
            ),
        )
        val root = json.parseToJsonElement(resp).jsonObject
        root["error"]?.let { err ->
            error(err.jsonObject["message"]?.jsonPrimitive?.content ?: "Anthropic API error")
        }
        // Only text blocks are the report — thinking blocks carry no text.
        val text = root["content"]!!.jsonArray
            .filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
            .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            .joinToString("\n")
            .trim()
        if (text.isBlank()) {
            val stop = root["stop_reason"]?.jsonPrimitive?.content
            error(when (stop) {
                "max_tokens" -> "The model used its whole token budget thinking and produced no report — press Analyse again."
                "refusal" -> "The model declined this request."
                else -> "The model returned an empty report (stop_reason: ${stop ?: "unknown"})."
            })
        }
        return text
    }

    private fun askGemini(apiKey: String, model: String, briefing: String): String {
        val body = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    putJsonArray("parts") { add(buildJsonObject { put("text", briefing) }) }
                })
            }
            putJsonObject("generationConfig") { put("maxOutputTokens", MAX_OUTPUT_TOKENS) }
        }.toString()

        val resp = post(
            url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            body = body,
            headers = mapOf("x-goog-api-key" to apiKey),
        )
        val root = json.parseToJsonElement(resp).jsonObject
        root["error"]?.let { err ->
            error(err.jsonObject["message"]?.jsonPrimitive?.content ?: "Gemini API error")
        }
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("Gemini returned no candidates.")
        val text = candidate["content"]?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            ?.joinToString("\n")
            ?.trim()
            .orEmpty()
        if (text.isBlank()) {
            val finish = candidate["finishReason"]?.jsonPrimitive?.content
            error(when (finish) {
                "MAX_TOKENS" -> "The model used its whole token budget thinking and produced no report — press Analyse again."
                else -> "The model returned an empty report (finishReason: ${finish ?: "unknown"})."
            })
        }
        return text
    }

    private fun post(url: String, body: String, headers: Map<String, String>): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.use { it.readText() }
                ?: error("HTTP ${conn.responseCode} with empty body")
        } finally {
            conn.disconnect()
        }
    }
}

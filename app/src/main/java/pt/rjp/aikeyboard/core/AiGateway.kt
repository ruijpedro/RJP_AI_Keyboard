package pt.rjp.aikeyboard.core

import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional AI bridge. The APK contains no provider secret.
 * Expected backend contract:
 * POST {"text":"...","language":"pt-PT","mode":"correct"}
 * -> {"text":"corrected text"}
 */
class AiGateway {
    fun correct(endpoint: String, language: Language, text: String): Result<String> = runCatching {
        require(endpoint.startsWith("https://")) { "O endpoint de IA deve usar HTTPS" }
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6000
            readTimeout = 12000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        val body = "{\"text\":\"${escape(text)}\",\"language\":\"${language.tag}\",\"mode\":\"correct\"}"
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) error("IA respondeu HTTP $code")
        Regex("\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
            .find(response)?.groupValues?.get(1)?.let(::unescape)
            ?: error("Resposta de IA inválida")
    }

    private fun escape(s: String): String = buildString {
        s.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }

    private fun unescape(s: String): String = s
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}

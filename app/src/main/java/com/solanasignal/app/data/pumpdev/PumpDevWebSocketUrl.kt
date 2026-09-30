package com.solanasignal.app.data.pumpdev

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object PumpDevWebSocketUrl {
    const val BASE_URL = "wss://pumpdev.io/ws"

    fun build(apiKey: String?): String {
        val key = apiKey?.trim().orEmpty()
        if (key.isEmpty()) return BASE_URL
        val encoded = URLEncoder.encode(key, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return "$BASE_URL?key=$encoded"
    }

    fun sanitize(urlOrMessage: String): String =
        urlOrMessage.replace(Regex("([?&]key=)[^&\\s]*", RegexOption.IGNORE_CASE), "$1[REDACTED]")
}

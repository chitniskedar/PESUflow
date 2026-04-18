package com.chitniskedar.pesufilter.network

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class Fetcher {

    fun fetchHtml(url: String, cookie: String? = null): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            useCaches = false
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Cache-Control", "no-cache")
            cookie?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie", it) }
        }

        return try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: throw IllegalStateException("Request failed with HTTP $statusCode")
            }

            BufferedReader(InputStreamReader(stream)).use { reader ->
                buildString {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        appendLine(line)
                    }
                }
            }.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Received empty HTML from backend")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val USER_AGENT = "Mozilla/5.0 (Android) PESUFlow/1.0"
    }
}

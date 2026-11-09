package com.biliflow.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Http {
    const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    @Volatile
    var cookie: String = ""

    data class TextResponse(
        val text: String,
        val setCookies: List<String>
    )

    suspend fun getAsync(
        url: String,
        referer: String = "https://www.bilibili.com/"
    ): String = withContext(Dispatchers.IO) { get(url, referer) }

    suspend fun getBytesAsync(
        url: String,
        referer: String = "https://www.bilibili.com/"
    ): ByteArray = withContext(Dispatchers.IO) { getBytes(url, referer) }

    suspend fun postFormAsync(
        url: String,
        fields: Map<String, String>,
        referer: String = "https://www.bilibili.com/"
    ): String = withContext(Dispatchers.IO) { postForm(url, fields, referer) }

    suspend fun postFormWithCookiesAsync(
        url: String,
        fields: Map<String, String>,
        referer: String = "https://www.bilibili.com/"
    ): TextResponse = withContext(Dispatchers.IO) {
        val body = fields.entries.joinToString("&") { (name, value) ->
            "${encode(name)}=${encode(value)}"
        }.toByteArray(Charsets.UTF_8)
        val response = requestResponse(
            url = normalizeUrl(url),
            referer = referer,
            accept = "application/json, text/plain, */*",
            method = "POST",
            body = body
        )
        TextResponse(
            text = response.body.toString(Charsets.UTF_8),
            setCookies = response.setCookies
        )
    }

    suspend fun getTextWithCookiesAsync(
        url: String,
        referer: String = "https://www.bilibili.com/"
    ): TextResponse = withContext(Dispatchers.IO) {
        val response = requestResponse(
            url = normalizeUrl(url),
            referer = referer,
            accept = "application/json, text/plain, */*",
            method = "GET",
            body = null
        )
        TextResponse(
            text = response.body.toString(Charsets.UTF_8),
            setCookies = response.setCookies
        )
    }

    fun getAnonymous(url: String, referer: String = "https://www.bilibili.com/"): String {
        val bytes = requestResponse(
            url = normalizeUrl(url),
            referer = referer,
            accept = "application/json, text/plain, */*",
            method = "GET",
            body = null,
            useCookie = false
        ).body
        return bytes.toString(Charsets.UTF_8)
    }

    fun get(url: String, referer: String = "https://www.bilibili.com/"): String {
        val bytes = request(
            url = normalizeUrl(url),
            referer = referer,
            accept = "application/json, text/plain, */*",
            method = "GET",
            body = null
        )
        return bytes.toString(Charsets.UTF_8)
    }

    fun getDesktop(url: String, referer: String = "https://www.bilibili.com/"): String {
        val bytes = requestResponse(
            url = normalizeUrl(url),
            referer = referer,
            accept = "application/json, text/plain, */*",
            method = "GET",
            body = null,
            userAgent = DESKTOP_UA
        ).body
        return bytes.toString(Charsets.UTF_8)
    }

    fun getBytes(url: String, referer: String = "https://www.bilibili.com/"): ByteArray {
        return request(
            url = normalizeUrl(url),
            referer = referer,
            accept = "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
            method = "GET",
            body = null
        )
    }

    fun postForm(
        url: String,
        fields: Map<String, String>,
        referer: String = "https://www.bilibili.com/"
    ): String {
        val body = fields.entries.joinToString("&") { (name, value) ->
            "${encode(name)}=${encode(value)}"
        }.toByteArray(Charsets.UTF_8)
        val bytes = request(
            url = normalizeUrl(url),
            referer = referer,
            accept = "application/json, text/plain, */*",
            method = "POST",
            body = body
        )
        return bytes.toString(Charsets.UTF_8)
    }

    private fun request(
        url: String,
        referer: String,
        accept: String,
        method: String,
        body: ByteArray?
    ): ByteArray = requestResponse(url, referer, accept, method, body).body

    private data class ResponseData(
        val body: ByteArray,
        val setCookies: List<String>
    )

    private fun requestResponse(
        url: String,
        referer: String,
        accept: String,
        method: String,
        body: ByteArray?,
        useCookie: Boolean = true,
        userAgent: String = UA
    ): ResponseData {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 25_000
                instanceFollowRedirects = true
                useCaches = false
                requestMethod = method
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Referer", referer)
                setRequestProperty("Accept", accept)
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                if (useCookie && cookie.isNotBlank()) setRequestProperty("Cookie", cookie)
                if (method == "POST") {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    setRequestProperty("Origin", "https://www.bilibili.com")
                    setRequestProperty("X-Requested-With", "XMLHttpRequest")
                }
            }
            if (body != null) {
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val setCookies = connection.headerFields
                ?.filterKeys { it?.equals("Set-Cookie", ignoreCase = true) == true }
                ?.values
                ?.flatten()
                .orEmpty()
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (code !in 200..299) {
                val text = response.toString(Charsets.UTF_8).take(240)
                android.util.Log.e(
                    "BiliFlow",
                    "HTTP $code for ${url.take(160)} body=${text.take(120)}"
                )
                throw IOException("HTTP $code $text")
            }
            return ResponseData(response, setCookies)
        } finally {
            connection?.disconnect()
        }
    }

    /** 通用请求：自定义方法/头/字节体（投稿上传、资料修改用） */
    fun raw(
        url: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        includeCookie: Boolean = true
    ): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(normalizeUrl(url)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                useCaches = false
                requestMethod = method
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json, text/plain, */*")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (includeCookie && cookie.isNotBlank()) setRequestProperty("Cookie", cookie)
            }
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes() }?.toString(Charsets.UTF_8) ?: ""
            if (code !in 200..299) {
                throw IOException("HTTP $code ${text.take(200)}")
            }
            return text
        } finally {
            connection?.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    fun normalizeUrl(url: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> "https://${url.removePrefix("http://")}"
            else -> url
        }
    }
}

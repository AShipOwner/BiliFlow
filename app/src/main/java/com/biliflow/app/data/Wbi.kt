package com.biliflow.app.data

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest

object Wbi {
    private val mixinKeyEncTab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    )

    private val mutex = Mutex()

    @Volatile
    private var mixinKey: String? = null

    @Volatile
    private var keyTime: Long = 0L

    private const val KEY_TTL_MS = 30 * 60 * 1000L

    suspend fun ensureSession() {
        mutex.withLock {
            if (Http.cookie.isBlank()) {
                runCatching {
                    val spi = JSONObject(Http.getAsync("https://api.bilibili.com/x/frontend/finger/spi"))
                    val data = spi.optJSONObject("data")
                    val b3 = data?.optString("b_3").orEmpty()
                    val b4 = data?.optString("b_4").orEmpty()
                    if (b3.isNotBlank()) {
                        Http.cookie = buildString {
                            append("buvid3=").append(b3)
                            if (b4.isNotBlank()) append("; buvid4=").append(b4)
                        }
                    }
                    Log.d("BiliFlow", "spi cookie ready=${b3.isNotBlank()} b4=${b4.isNotBlank()}")
                }.onFailure {
                    Log.e("BiliFlow", "spi request failed", it)
                }
            }

            val now = System.currentTimeMillis()
            if (mixinKey != null && now - keyTime < KEY_TTL_MS) return@withLock

            val nav = JSONObject(Http.getAsync("https://api.bilibili.com/x/web-interface/nav"))
            val wbi = nav.optJSONObject("data")?.optJSONObject("wbi_img")
                ?: throw IOException("无法获取 WBI 签名密钥（code=${nav.optInt("code")}）")
            val img = wbi.optString("img_url").substringAfterLast('/').substringBefore('.')
            val sub = wbi.optString("sub_url").substringAfterLast('/').substringBefore('.')
            val raw = img + sub
            if (raw.length < 64) throw IOException("WBI 密钥格式异常（len=${raw.length}）")
            mixinKey = mixinKeyEncTab.joinToString("") { raw[it].toString() }.take(32)
            keyTime = now
            Log.d("BiliFlow", "wbi key ready img=${img.length} sub=${sub.length}")
        }
    }

    suspend fun sign(params: Map<String, String>): String {
        ensureSession()
        val key = mixinKey ?: throw IOException("WBI 签名密钥不可用")
        val all = params.toMutableMap().apply {
            put("wts", (System.currentTimeMillis() / 1000L).toString())
        }
        val query = all.entries
            .sortedBy { it.key }
            .joinToString("&") { (name, value) ->
                "${encode(name)}=${encode(value.filterNot { it in "!'()*" })}"
            }
        val rid = md5(query + key)
        return "$query&w_rid=$rid"
    }

    fun invalidate() {
        mixinKey = null
        keyTime = 0L
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

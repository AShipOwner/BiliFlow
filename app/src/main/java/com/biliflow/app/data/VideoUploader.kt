package com.biliflow.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder

/**
 * B 站 Web 投稿上传（upos 分片）。
 * 流程：preupload → POST 元数据 → PUT 分片 → POST 结束 → x/vu/web/add/v3。
 * 依据 bilibili-API-collect/docs/creativecenter/upload.md 实现，单线程、10MB 分片。
 */
object VideoUploader {

    data class Result(val aid: Long, val bvid: String)

    private data class Preupload(
        val auth: String,
        val endpoint: String,
        val uposUri: String,
        val bizId: Long,
        val chunkSize: Int
    )

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun uposBase(pre: Preupload): String {
        val host = "https:" + pre.endpoint
        val path = pre.uposUri.replaceFirst("upos://", "/")
        return host + path
    }

    suspend fun upload(
        context: Context,
        uri: Uri,
        title: String,
        desc: String,
        tid: Int,
        tag: String,
        isOnlySelf: Boolean = false,
        onProgress: (Float) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        if (!AuthManager.isLoggedIn) throw BiliApi.BiliException(-101, "请先登录")

        val resolver = context.contentResolver
        var size = 0L
        var displayName = "upload.mp4"
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
            }
        }
        if (size <= 0L) size = resolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
        if (size <= 0L) throw IllegalStateException("无法读取视频文件大小")

        // 1) preupload
        val preUrl = "https://member.bilibili.com/preupload?name=${enc(displayName)}" +
            "&size=$size&r=upos&profile=ugcfx/bup&ssl=0&version=2.14.0.0&build=2140000"
        val preJson = JSONObject(Http.raw(preUrl, "GET"))
        if (preJson.optInt("OK", 0) != 1) {
            throw IllegalStateException(preJson.optString("message", "预上传失败"))
        }
        val pre = Preupload(
            auth = preJson.optString("auth"),
            endpoint = preJson.optString("endpoint"),
            uposUri = preJson.optString("upos_uri"),
            bizId = preJson.optLong("biz_id"),
            chunkSize = preJson.optInt("chunk_size", 10 * 1024 * 1024).coerceAtLeast(1024 * 1024)
        )
        val base = uposBase(pre)

        // 2) POST 元数据
        val metaUrl = "$base?uploads=&output=json&profile=${enc("ugcfx/bup")}" +
            "&filesize=$size&partsize=${pre.chunkSize}&biz_id=${pre.bizId}"
        val metaJson = JSONObject(
            Http.raw(metaUrl, "POST", mapOf("X-Upos-Auth" to pre.auth))
        )
        val uploadId = metaJson.optString("upload_id")
        if (uploadId.isBlank()) throw IllegalStateException("上传初始化失败")

        // 3) 分片上传
        val chunks = ((size + pre.chunkSize - 1) / pre.chunkSize).toInt()
        val buffer = ByteArray(pre.chunkSize)
        resolver.openInputStream(uri)?.use { input ->
            var chunk = 0
            var uploaded = 0L
            while (true) {
                var read = 0
                while (read < buffer.size) {
                    val n = input.read(buffer, read, buffer.size - read)
                    if (n <= 0) break
                    read += n
                }
                if (read <= 0) break
                val partNumber = chunk + 1
                val start = uploaded
                val end = uploaded + read
                val putUrl = "$base?partNumber=$partNumber&uploadId=${enc(uploadId)}" +
                    "&chunk=$chunk&chunks=$chunks&size=$read&start=$start&end=$end&total=$size"
                Http.raw(
                    putUrl,
                    "PUT",
                    mapOf(
                        "X-Upos-Auth" to pre.auth,
                        "Content-Type" to "application/octet-stream"
                    ),
                    buffer.copyOf(read)
                )
                chunk += 1
                uploaded = end
                onProgress((uploaded.toFloat() / size.toFloat()).coerceIn(0f, 1f))
            }
        } ?: throw IllegalStateException("无法读取视频流")

        // 4) 结束上传
        val parts = JSONArray()
        for (i in 1..chunks) {
            parts.put(JSONObject().put("partNumber", i).put("eTag", "etag"))
        }
        val endUrl = "$base?output=json&name=${enc(displayName)}&profile=${enc("ugcfx/bup")}" +
            "&uploadId=${enc(uploadId)}&biz_id=${pre.bizId}"
        Http.raw(
            endUrl,
            "POST",
            mapOf(
                "X-Upos-Auth" to pre.auth,
                "Content-Type" to "application/json; charset=UTF-8"
            ),
            JSONObject().put("parts", parts).toString().toByteArray(Charsets.UTF_8)
        )

        // 5) 投递稿件
        val filename = pre.uposUri.substringAfterLast('/').substringBeforeLast('.')
        val videos = JSONArray().put(
            JSONObject()
                .put("filename", filename)
                .put("title", title)
                .put("desc", desc)
                .put("cid", pre.bizId)
        )
        val body = JSONObject()
            .put("videos", videos)
            .put("cover", "")
            .put("cover43", "")
            .put("title", title)
            .put("copyright", 1)
            .put("tid", tid)
            .put("tag", tag.ifBlank { "BiliFlow" })
            .put("desc_format_id", 9999)
            .put("desc", desc)
            .put("recreate", -1)
            .put("dynamic", "")
            .put("interactive", 0)
            .put("act_reserve_create", 0)
            .put("no_disturbance", 0)
            .put("no_reprint", 1)
            .put("subtitle", JSONObject().put("open", 0).put("lan", ""))
            .put("dolby", 0)
            .put("lossless_music", 0)
            .put("up_selection_reply", false)
            .put("up_close_reply", false)
            .put("up_close_danmu", false)
            .put("web_os", 3)
            .put("is_only_self", if (isOnlySelf) 1 else 0)
            .put("csrf", AuthManager.csrf)

        val addUrl = "https://member.bilibili.com/x/vu/web/add/v3?ts=${System.currentTimeMillis()}" +
            "&csrf=${enc(AuthManager.csrf)}"
        val addJson = JSONObject(
            Http.raw(
                addUrl,
                "POST",
                mapOf("Content-Type" to "application/json; charset=utf-8"),
                body.toString().toByteArray(Charsets.UTF_8)
            )
        )
        if (addJson.optInt("code", -1) != 0) {
            throw BiliApi.BiliException(addJson.optInt("code"), addJson.optString("message", "投稿失败"))
        }
        val data = addJson.optJSONObject("data") ?: JSONObject()
        onProgress(1f)
        Result(aid = data.optLong("aid"), bvid = data.optString("bvid"))
    }
}

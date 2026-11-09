package com.biliflow.app.data

import android.text.Html
import android.util.Log
import android.util.Xml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Inflater

object BiliApi {
    private const val API = "https://api.bilibili.com"
    private const val REFERER = "https://www.bilibili.com/"
    private const val DANMAKU_TTL_MS = 10 * 60 * 1000L

    private val danmakuCache = ConcurrentHashMap<Long, CachedDanmaku>()

    // DASH 失败记忆：避免每次滑动都先撞一次 CDN 403 再回退
    private val dashFailedAt = ConcurrentHashMap<String, Long>()
    @Volatile
    private var dashBlockedUntil = 0L
    private var dashFailStreak = 0

    fun noteDashFailure(bvid: String) {
        if (bvid.isBlank()) return
        dashFailedAt[bvid] = System.currentTimeMillis()
        synchronized(this) {
            dashFailStreak += 1
            if (dashFailStreak >= 3) {
                dashBlockedUntil = System.currentTimeMillis() + 10 * 60_000L
                dashFailStreak = 0
            }
        }
    }

    private fun dashUsable(bvid: String): Boolean {
        if (System.currentTimeMillis() < dashBlockedUntil) return false
        val failedAt = dashFailedAt[bvid] ?: return true
        return System.currentTimeMillis() - failedAt > 10 * 60_000L
    }

    @Volatile
    private var favoriteFolderId: Long = 0L

    class BiliException(val code: Int, message: String) : IOException(message)

    // ---------------------------------------------------------------- feeds

    suspend fun recommend(freshIdx: Int): List<VideoItem> {
        val salt = (System.currentTimeMillis() / 60_000L).toInt()
        val idx = freshIdx * 10 + (salt % 7)
        val data = check(
            getJson(
                "$API/x/web-interface/index/top/feed/rcmd" +
                    "?ps=8&fresh_type=4&feed_version=V8&plat=1" +
                    "&fresh_idx=$idx&fresh_idx_1h=$freshIdx&brush=0&web_location=1430650"
            )
        )
        val array = data.optJSONArray("item") ?: return emptyList()
        val result = ArrayList<VideoItem>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val goto = item.stringOf("goto")
            if (goto.isNotBlank() && goto != "av") continue
            if (isAdItem(item)) continue
            parseRecommend(item)?.let(result::add)
        }
        return result.distinctBy { it.key }
    }

    suspend fun popular(page: Int): List<VideoItem> {
        val data = check(getJson("$API/x/web-interface/popular?ps=12&pn=$page"))
        val array = data.optJSONArray("list") ?: return emptyList()
        val result = ArrayList<VideoItem>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            parsePopular(item)?.let(result::add)
        }
        return result.distinctBy { it.key }
    }

    suspend fun followFeed(offset: String?): FollowPage {
        if (!AuthManager.isLoggedIn) throw BiliException(-101, "请先登录")
        val params = mutableMapOf(
            "type" to "video",
            "page" to "1",
            "timezone_offset" to "-480",
            "features" to "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote",
            "web_location" to "333.999"
        )
        if (!offset.isNullOrBlank()) params["offset"] = offset
        val query = Wbi.sign(params)
        val data = check(getJson("$API/x/polymer/web-dynamic/v1/feed/all?$query"))
        val array = data.optJSONArray("items") ?: return FollowPage()
        val result = ArrayList<VideoItem>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            parseDynamicVideo(item)?.let(result::add)
        }
        return FollowPage(
            items = result.distinctBy { it.key },
            nextOffset = data.stringOf("offset"),
            hasMore = data.boolOf("has_more")
        )
    }

    // ---------------------------------------------------------------- search

    suspend fun search(keyword: String, page: Int): List<VideoItem> {
        val params = mapOf(
            "search_type" to "video",
            "keyword" to keyword,
            "page" to page.toString(),
            "page_size" to "20"
        )
        val data = try {
            val query = Wbi.sign(params)
            check(getJson("$API/x/web-interface/wbi/search/type?$query"))
        } catch (error: BiliException) {
            Log.e("BiliFlow", "search api code=${error.code} message=${error.message}")
            if (error.code == -352 || error.code == -403 || error.code == -412) {
                Wbi.invalidate()
                val query = Wbi.sign(params)
                check(getJson("$API/x/web-interface/wbi/search/type?$query"))
            } else {
                throw error
            }
        }

        if (data.has("v_voucher")) {
            throw BiliException(-352, "该关键词触发平台风控，请换一个关键词")
        }

        val array = data.optJSONArray("result") ?: return emptyList()
        val result = ArrayList<VideoItem>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            parseSearch(item)?.let(result::add)
        }
        return result.distinctBy { it.key }
    }

    // ---------------------------------------------------------------- video

    suspend fun detail(bvid: String): VideoItem {
        val data = check(getJson("$API/x/web-interface/view?bvid=$bvid"))
        return parseDetail(data)
    }

    suspend fun playUrl(
        bvid: String,
        cid: Long,
        qn: Int,
        forceProgressive: Boolean = false
    ): PlayInfo {
        // DASH 画质与音频分离，清晰度更贴近所选；未登录或该视频 DASH 曾失败时直接走渐进流。
        if (!forceProgressive && (qn >= 64 || AuthManager.isLoggedIn) && dashUsable(bvid)) {
            val dash = runCatching { dashPlayInfo(bvid, cid, qn) }.getOrNull()
            if (dash != null) {
                synchronized(this) { dashFailStreak = 0 }
                if (dash.quality <= qn.coerceAtLeast(16)) return dash
                if (qn >= 64) return dash
            } else {
                noteDashFailure(bvid)
            }
        }

        var lastError: Exception? = null
        for (quality in listOf(qn.coerceAtMost(64), 64, 32, 16).distinct()) {
            try {
                val data = check(
                    getJson(
                        "$API/x/player/playurl?bvid=$bvid&cid=$cid&qn=$quality" +
                            "&fnval=1&platform=html5&high_quality=1&fourk=1"
                    )
                )
                val durl = data.optJSONArray("durl") ?: continue
                val urls = ArrayList<String>(durl.length())
                for (index in 0 until durl.length()) {
                    val url = durl.optJSONObject(index)?.stringOf("url").orEmpty()
                    if (url.isNotBlank()) urls.add(Http.normalizeUrl(url))
                }
                if (urls.isNotEmpty()) {
                    val info = PlayInfo(
                        urls = urls,
                        quality = data.intOf("quality", quality),
                        lengthMs = data.longOf("timelength")
                    )
                    Log.d(
                        "BiliFlow",
                        "play progressive bvid=$bvid requested=$qn actual=${info.quality}"
                    )
                    return info
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastError = error
            }
        }

        if (!forceProgressive) {
            runCatching { dashPlayInfo(bvid, cid, qn) }.getOrNull()?.let { return it }
        }
        throw lastError ?: IOException("暂时没有可用的播放地址")
    }

    // ---------------------------------------------------------------- account state

    suspend fun relationState(bvid: String): SocialState {
        val data = check(getJson("$API/x/web-interface/archive/relation?bvid=$bvid"))
        return SocialState(
            liked = data.boolOf("like"),
            coinCount = data.intOf("coin"),
            favorite = data.boolOf("favorite"),
            following = data.boolOf("attention"),
            loaded = true
        )
    }

    suspend fun toggleLike(item: VideoItem, like: Boolean) {
        val fields = mutableMapOf(
            "bvid" to item.bvid,
            "like" to if (like) "1" else "2"
        )
        if (item.aid > 0L) fields["aid"] = item.aid.toString()
        check(postAuth("/x/web-interface/archive/like", fields, item.webUrl))
    }

    suspend fun addCoin(item: VideoItem, count: Int, alsoLike: Boolean) {
        val fields = mutableMapOf(
            "bvid" to item.bvid,
            "multiply" to count.coerceIn(1, 2).toString(),
            "select_like" to if (alsoLike) "1" else "0"
        )
        if (item.aid > 0L) fields["aid"] = item.aid.toString()
        check(postAuth("/x/web-interface/coin/add", fields, item.webUrl))
    }

    suspend fun setFavorite(item: VideoItem, favorite: Boolean) {
        val aid = if (item.aid > 0L) item.aid else detail(item.bvid).aid
        if (aid <= 0L) throw BiliException(-1, "无法获取视频 aid")
        val folder = ensureFavoriteFolder()
        val fields = mutableMapOf(
            "rid" to aid.toString(),
            "type" to "2",
            "platform" to "web"
        )
        if (favorite) {
            fields["add_media_ids"] = folder.toString()
        } else {
            fields["del_media_ids"] = folder.toString()
        }
        check(postAuth("/x/v3/fav/resource/deal", fields, item.webUrl))
    }

    suspend fun followUser(mid: Long, follow: Boolean) {
        if (mid <= 0L) throw BiliException(-1, "用户信息不完整")
        check(
            postAuth(
                "/x/relation/modify",
                mapOf(
                    "fid" to mid.toString(),
                    "act" to if (follow) "1" else "2",
                    "re_src" to "11"
                ),
                "https://space.bilibili.com/$mid"
            )
        )
    }

    private suspend fun ensureFavoriteFolder(): Long {
        favoriteFolderId.takeIf { it > 0L }?.let { return it }
        val data = check(
            getJson("$API/x/v3/fav/folder/created/list-all?up_mid=${AuthManager.mid}")
        )
        val list = data.optJSONArray("list")
        if (list != null && list.length() > 0) {
            val id = list.optJSONObject(0)?.longOf("id") ?: 0L
            if (id > 0L) {
                favoriteFolderId = id
                return id
            }
        }
        val created = check(
            postAuth(
                "/x/v3/fav/folder/add",
                mapOf("title" to "默认收藏夹", "privacy" to "0")
            )
        )
        val id = created.longOf("id")
        if (id <= 0L) throw BiliException(-1, "无法创建默认收藏夹")
        favoriteFolderId = id
        return id
    }

    // ---------------------------------------------------------------- comments

    enum class CommentVisibility { VISIBLE, SELF_ONLY, MISSING, UNKNOWN }

    /**
     * 评论反诈自检（参考 freedom-introvert/biliSendCommAntifraud 的思路）：
     * 同一接口分别用「登录态」和「匿名」查一遍，看这条 rpid 是否对别人可见。
     */
    suspend fun checkCommentVisibility(aid: Long, rpid: Long): CommentVisibility {
        if (aid <= 0L || rpid <= 0L) return CommentVisibility.UNKNOWN
        val path = "$API/x/v2/reply/main?oid=$aid&type=1&mode=3&next=0&seek_rpid=$rpid&plat=1"
        val withCookie = runCatching { JSONObject(Http.get(path)) }.getOrNull()
        val anonymous = runCatching {
            withContext(Dispatchers.IO) { JSONObject(Http.getAnonymous(path)) }
        }.getOrNull()
        fun JsonObjectHasRpid(response: JSONObject?): Boolean? {
            val data = response?.optJSONObject("data") ?: return null
            if (response.optInt("code", -1) != 0) return null
            fun scan(array: org.json.JSONArray?): Boolean {
                if (array == null) return false
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    if (item.longOf("rpid") == rpid) return true
                }
                return false
            }
            if (scan(data.optJSONArray("replies"))) return true
            if (scan(data.optJSONArray("top_replies"))) return true
            val cursor = data.optJSONObject("cursor")?.optJSONObject("pagination_reply")
            return cursor != null && scan(data.optJSONArray("replies"))
        }
        val self = JsonObjectHasRpid(withCookie)
        val other = JsonObjectHasRpid(anonymous)
        return when {
            other == true -> CommentVisibility.VISIBLE
            other == false && self == true -> CommentVisibility.SELF_ONLY
            other == false && self == false -> CommentVisibility.MISSING
            else -> CommentVisibility.UNKNOWN
        }
    }

    suspend fun comments(aid: Long, page: Int): List<CommentItem> =
        commentPage(aid, page, 2).items

    suspend fun commentPage(aid: Long, page: Int, sort: Int): CommentPage {
        val data = check(
            getJson(
                "$API/x/v2/reply?type=1&oid=$aid&sort=$sort&ps=20&pn=$page&nohot=0"
            )
        )
        val repliesArray = data.optJSONArray("replies")
        val items = ArrayList<CommentItem>()
        if (repliesArray != null) {
            for (index in 0 until repliesArray.length()) {
                repliesArray.optJSONObject(index)?.let { parseComment(it) }?.let(items::add)
            }
        }

        val top = ArrayList<CommentItem>()
        val topArray = data.optJSONArray("top_replies")
        if (topArray != null) {
            for (index in 0 until topArray.length()) {
                topArray.optJSONObject(index)?.let { parseComment(it) }?.let(top::add)
            }
        }
        data.optJSONObject("upper")?.optJSONObject("top")
            ?.let { parseComment(it) }
            ?.let { upperTop ->
                if (top.none { it.rpid == upperTop.rpid }) {
                    top.add(upperTop.copy(isUpTop = true))
                }
            }

        val pageObject = data.optJSONObject("page")
        val total = pageObject?.longOf("acount") ?: 0L
        val hasMore = if (total > 0L) {
            page * 20L < total
        } else {
            items.size >= 20
        }
        return CommentPage(
            items = items,
            topItems = top.distinctBy { it.rpid },
            total = total,
            hasMore = hasMore
        )
    }

    suspend fun commentReplies(aid: Long, root: Long, page: Int): CommentPage {
        val data = check(
            getJson(
                "$API/x/v2/reply/reply?type=1&oid=$aid&root=$root&ps=20&pn=$page"
            )
        )
        val array = data.optJSONArray("replies") ?: return CommentPage()
        val items = ArrayList<CommentItem>(array.length())
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { parseComment(it) }?.let(items::add)
        }
        val pageObject = data.optJSONObject("page")
        val total = pageObject?.longOf("acount") ?: 0L
        return CommentPage(
            items = items,
            total = total,
            hasMore = if (total > 0L) page * 20L < total else items.size >= 20
        )
    }

    suspend fun postComment(
        aid: Long,
        message: String,
        root: Long = 0L,
        parent: Long = 0L
    ): Long {
        val fields = mutableMapOf(
            "oid" to aid.toString(),
            "type" to "1",
            "message" to message,
            "plat" to "1"
        )
        if (root > 0L) fields["root"] = root.toString()
        if (parent > 0L) fields["parent"] = parent.toString()
        val data = check(postAuth("/x/v2/reply/add", fields))
        return data.optLong("rpid", 0L)
    }

    suspend fun likeComment(aid: Long, rpid: Long, like: Boolean) {
        check(
            postAuth(
                "/x/v2/reply/action",
                mapOf(
                    "oid" to aid.toString(),
                    "type" to "1",
                    "rpid" to rpid.toString(),
                    "action" to if (like) "1" else "0"
                )
            )
        )
    }

    suspend fun deleteComment(aid: Long, rpid: Long) {
        check(
            postAuth(
                "/x/v2/reply/del",
                mapOf(
                    "oid" to aid.toString(),
                    "type" to "1",
                    "rpid" to rpid.toString()
                )
            )
        )
    }

    // ---------------------------------------------------------------- danmaku

    suspend fun danmaku(cid: Long, aid: Long): List<DanmakuItem> {
        if (cid <= 0L) return emptyList()
        danmakuCache[cid]
            ?.takeIf { System.currentTimeMillis() - it.createdAt < DANMAKU_TTL_MS }
            ?.let { return it.items }

        return try {
            val raw = Http.getBytesAsync(
                "$API/x/v1/dm/list.so?oid=$cid",
                referer = "https://www.bilibili.com/video/av$aid"
            )
            val xml = if (startsWithAscii(raw, "<?xml") || startsWithAscii(raw, "<i>")) {
                raw
            } else {
                inflate(raw)
            }
            val items = parseDanmakuXml(xml)
            danmakuCache[cid] = CachedDanmaku(System.currentTimeMillis(), items)
            items
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("BiliFlow", "danmaku load failed cid=$cid", error)
            emptyList()
        }
    }

    suspend fun sendDanmaku(
        cid: Long,
        bvid: String,
        message: String,
        progressMs: Long
    ): Long {
        val data = check(
            postAuth(
                "/x/v2/dm/post",
                mapOf(
                    "type" to "1",
                    "oid" to cid.toString(),
                    "msg" to message,
                    "bvid" to bvid,
                    "progress" to progressMs.coerceAtLeast(0L).toString(),
                    "mode" to "1",
                    "fontsize" to "25",
                    "color" to "16777215",
                    "pool" to "0",
                    "plat" to "1",
                    "rnd" to (System.currentTimeMillis() / 1000L).toString(),
                    "dm_type" to "1"
                ),
                "https://www.bilibili.com/video/$bvid"
            )
        )
        return data.longOf("id").takeIf { it > 0L }
            ?: data.stringOf("idStr").toLongOrNull()
            ?: 0L
    }

    suspend fun recallDanmaku(cid: Long, dmid: Long) {
        check(
            postAuth(
                "/x/dm/recall",
                mapOf(
                    "cid" to cid.toString(),
                    "dmid" to dmid.toString()
                )
            )
        )
    }

    // ------------------------------------------------------- live / messages / space

    suspend fun liveRooms(): List<LiveRoom> {
        val data = check(
            getJson(
                "https://api.live.bilibili.com/xlive/web-interface/v1/index/getList?platform=web",
                "https://live.bilibili.com/"
            )
        )
        val rooms = ArrayList<LiveRoom>()
        for (key in listOf("recommend_room_list", "room_list")) {
            val array = data.optJSONArray(key) ?: continue
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val roomId = item.longOf("roomid")
                if (roomId <= 0L) continue
                rooms.add(
                    LiveRoom(
                        roomId = roomId,
                        title = item.stringOf("title"),
                        uname = item.stringOf("uname"),
                        face = Http.normalizeUrl(item.stringOf("face")),
                        cover = Http.normalizeUrl(item.stringOf("cover")),
                        online = item.longOf("online"),
                        area = item.stringOf("area_v2_name"),
                        mid = item.longOf("mid")
                    )
                )
            }
        }
        return rooms.distinctBy { it.roomId }
    }

    suspend fun livePlayUrl(roomId: Long): List<String> {
        val data = check(
            getJson(
                "https://api.live.bilibili.com/room/v1/Room/playUrl?cid=$roomId&quality=4&platform=web",
                "https://live.bilibili.com/"
            )
        )
        val durl = data.optJSONArray("durl") ?: return emptyList()
        val urls = ArrayList<String>()
        for (index in 0 until durl.length()) {
            val url = durl.optJSONObject(index)?.stringOf("url").orEmpty()
            if (url.isNotBlank()) urls.add(Http.normalizeUrl(url))
        }
        return urls
    }

    suspend fun messageFeed(kind: String): List<MessageItem> {
        val data = check(getJson("$API/x/msgfeed/$kind?platform=web&build=0&mobi_app=web"))
        val array = data.optJSONArray("items") ?: return emptyList()
        val result = ArrayList<MessageItem>()
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val user = entry.optJSONObject("user")
            val item = entry.optJSONObject("item")
            val time = when (kind) {
                "reply" -> entry.longOf("reply_time")
                "at" -> entry.longOf("at_time")
                else -> entry.longOf("last_time")
            }
            result.add(
                MessageItem(
                    id = entry.stringOf("id").ifBlank { index.toString() },
                    nickname = user?.stringOf("nickname").orEmpty().ifBlank { "B 站用户" },
                    face = Http.normalizeUrl(user?.stringOf("avatar").orEmpty()),
                    content = item?.stringOf("source_content").orEmpty()
                        .ifBlank { item?.stringOf("title").orEmpty() },
                    title = item?.stringOf("title").orEmpty(),
                    time = time,
                    isNew = entry.boolOf("is_new"),
                    uri = item?.stringOf("uri").orEmpty(),
                    mid = user?.longOf("mid") ?: 0L
                )
            )
        }
        return result
    }

    suspend fun likeFeed(): List<MessageItem> {
        val data = check(getJson("$API/x/msgfeed/like?platform=web&build=0&mobi_app=web"))
        val array = data.optJSONObject("latest")?.optJSONArray("items") ?: return emptyList()
        val result = ArrayList<MessageItem>()
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val users = entry.optJSONArray("users")
            val first = users?.optJSONObject(0)
            val item = entry.optJSONObject("item")
            val suffix = if (users != null && users.length() > 1) " 等${users.length()}人" else ""
            result.add(
                MessageItem(
                    id = entry.stringOf("id").ifBlank { index.toString() },
                    nickname = first?.stringOf("nickname").orEmpty().ifBlank { "B 站用户" } + suffix,
                    face = Http.normalizeUrl(first?.stringOf("avatar").orEmpty()),
                    content = item?.stringOf("title").orEmpty()
                        .ifBlank { item?.stringOf("desc").orEmpty() },
                    title = "赞了你的内容",
                    time = entry.longOf("like_time"),
                    uri = item?.stringOf("uri").orEmpty(),
                    mid = first?.longOf("mid") ?: 0L
                )
            )
        }
        return result
    }

    /** 关注列表（attribute=6 表示互相关注） */
    suspend fun followings(mid: Long, page: Int): List<FollowUser> {
        val data = check(getJson("$API/x/relation/followings?vmid=$mid&pn=$page&ps=50&order=desc"))
        val array = data.optJSONArray("list") ?: return emptyList()
        val result = ArrayList<FollowUser>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val uid = item.longOf("mid")
            if (uid <= 0L) continue
            result.add(
                FollowUser(
                    mid = uid,
                    name = item.stringOf("uname"),
                    face = Http.normalizeUrl(item.stringOf("face")),
                    mutual = item.intOf("attribute") == 6
                )
            )
        }
        return result
    }

    /** 发送私信（web_im） */
    suspend fun sendWhisper(talkerId: Long, text: String) {
        if (!AuthManager.isLoggedIn) throw BiliException(-101, "请先登录")
        val content = JSONObject().put("content", text).toString()
        val devId = java.util.UUID.randomUUID().toString().uppercase()
        val fields = mapOf(
            "msg[sender_uid]" to AuthManager.mid.toString(),
            "msg[receiver_id]" to talkerId.toString(),
            "msg[receiver_type]" to "1",
            "msg[msg_type]" to "1",
            "msg[msg_status]" to "0",
            "msg[dev_id]" to devId,
            "msg[timestamp]" to (System.currentTimeMillis() / 1000L).toString(),
            "msg[content]" to content,
            "csrf" to AuthManager.csrf,
            "csrf_token" to AuthManager.csrf,
            "build" to "0",
            "mobi_app" to "web",
            "platform" to "web"
        )
        withContext(Dispatchers.IO) {
            JSONObject(
                Http.postForm(
                    "https://api.vc.bilibili.com/web_im/v1/web_im/send_msg",
                    fields,
                    "https://message.bilibili.com/"
                )
            )
        }.let { response ->
            if (response.optInt("code", -1) != 0) {
                throw BiliException(response.optInt("code"), response.optString("message", "发送失败"))
            }
        }
    }

    /** 系统消息 */
    suspend fun systemMessages(): List<MessageItem> {
        val response = getJson(
            "https://message.bilibili.com/x/sys-msg/query_notify_list" +
                "?platform=web&page=1&page_size=30",
            referer = "https://message.bilibili.com/"
        )
        val code = response.optInt("code", -1)
        if (code != 0) {
            throw BiliException(code, response.optString("message", "请求失败"))
        }
        val array = response.optJSONArray("data") ?: return emptyList()
        val timeFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
        val linkRegex = Regex("#\\{([^}]*)\\}\\{\"([^\"]+)\"\\}")
        val result = ArrayList<MessageItem>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            var body = item.stringOf("content")
            if (body.trimStart().startsWith("{")) {
                runCatching {
                    val json = JSONObject(body)
                    body = json.optString("web").ifBlank { json.optString("text") }.ifBlank { body }
                }
            }
            val link = linkRegex.find(body)?.groupValues?.getOrNull(2)
                ?: item.stringOf("card_link")
            body = linkRegex.replace(body, "$1").trim()
            val time = runCatching {
                timeFormat.parse(item.stringOf("time_at"))?.time?.div(1000L) ?: 0L
            }.getOrDefault(0L)
            result.add(
                MessageItem(
                    id = item.stringOf("id").ifBlank { index.toString() },
                    nickname = item.optJSONObject("source")?.stringOf("name").orEmpty(),
                    content = body.ifBlank { item.stringOf("title") },
                    title = item.stringOf("title").ifBlank { "系统通知" },
                    time = time,
                    uri = link,
                    mid = 0L
                )
            )
        }
        return result
    }

    /** 用户「喜欢」的视频 */
    suspend fun likedVideos(mid: Long, page: Int): List<VideoItem> {
        val query = Wbi.sign(
            mapOf(
                "vmid" to mid.toString(),
                "ps" to "30",
                "pn" to page.toString(),
                "order" to "pubdate"
            )
        )
        val data = check(getJson("$API/x/space/like/video?$query"))
        val array = data.optJSONObject("list")?.optJSONArray("vlist") ?: return emptyList()
        val result = ArrayList<VideoItem>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val bvid = item.stringOf("bvid")
            if (bvid.isBlank()) continue
            result.add(
                VideoItem(
                    bvid = bvid,
                    aid = item.longOf("aid"),
                    title = item.stringOf("title"),
                    cover = Http.normalizeUrl(item.stringOf("pic")),
                    ownerName = item.stringOf("author"),
                    ownerMid = item.longOf("mid", mid),
                    view = item.longOf("play"),
                    durationSec = parseDuration(item.stringOf("length")),
                    pubdate = item.longOf("created")
                )
            )
        }
        return result
    }

    suspend fun fansList(page: Int): List<FanItem> {
        val data = check(
            getJson("$API/x/relation/followers?vmid=${AuthManager.mid}&pn=$page&ps=30&order=desc")
        )
        val array = data.optJSONArray("list") ?: return emptyList()
        val result = ArrayList<FanItem>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            result.add(
                FanItem(
                    mid = item.longOf("mid"),
                    name = item.stringOf("uname"),
                    face = Http.normalizeUrl(item.stringOf("face")),
                    sign = item.stringOf("sign")
                )
            )
        }
        return result
    }

    suspend fun whisperSessions(sessionType: Int = 1): List<WhisperSession> {
        val data = check(
            getJson(
                "https://api.vc.bilibili.com/session_svr/v1/session_svr/get_sessions" +
                    "?session_type=$sessionType&group_fold=1&unfollow_fold=0&sort_rule=2&build=0&mobi_app=web"
            )
        )
        val array = data.optJSONArray("session_list") ?: return emptyList()
        val result = ArrayList<WhisperSession>()
        for (index in 0 until array.length()) {
            val session = array.optJSONObject(index) ?: continue
            val talkerId = session.longOf("talker_id")
            if (talkerId <= 0L) continue
            val lastMsg = session.optJSONObject("last_msg")
            val card = runCatching { userCard(talkerId) }.getOrNull()
            result.add(
                WhisperSession(
                    talkerId = talkerId,
                    name = card?.first ?: session.stringOf("group_name").ifBlank { "用户 $talkerId" },
                    face = card?.second.orEmpty(),
                    lastMsg = parseMsgContent(lastMsg?.stringOf("content").orEmpty()),
                    unread = session.intOf("unread_count"),
                    ts = (lastMsg?.longOf("timestamp") ?: session.longOf("session_ts")) / 1000L
                )
            )
        }
        return result
    }

    suspend fun chatMessages(talkerId: Long): List<ChatMessage> {
        val data = check(
            getJson(
                "https://api.vc.bilibili.com/svr_sync/v1/svr_sync/fetch_session_msgs" +
                    "?talker_id=$talkerId&session_type=1&size=30&build=0&mobi_app=web"
            )
        )
        val array = data.optJSONArray("messages") ?: return emptyList()
        val result = ArrayList<ChatMessage>()
        for (index in 0 until array.length()) {
            val msg = array.optJSONObject(index) ?: continue
            val sender = msg.longOf("sender_uid")
            result.add(
                ChatMessage(
                    senderUid = sender,
                    content = parseMsgContent(msg.stringOf("content")),
                    ts = msg.longOf("timestamp"),
                    isMine = sender == AuthManager.mid
                )
            )
        }
        return result.reversed()
    }

    private fun parseMsgContent(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching {
            JSONObject(raw).optString("content").ifBlank { raw }
        }.getOrDefault(raw)
    }

    suspend fun userCard(mid: Long): Pair<String, String> {
        val data = check(getJson("$API/x/web-interface/card?mid=$mid"))
        val card = data.optJSONObject("card")
        return Pair(
            card?.stringOf("name").orEmpty(),
            Http.normalizeUrl(card?.stringOf("face").orEmpty())
        )
    }

    /** 主页聚合信息：背景图 / 获赞 / 关注 / 粉丝 / 投稿数 */
    /** 修改昵称 / 签名（网页版 member 接口） */
    suspend fun updateProfile(uname: String, sign: String) {
        if (!AuthManager.isLoggedIn) throw BiliException(-101, "请先登录")
        val fields = mapOf(
            "uname" to uname,
            "usersign" to sign,
            "csrf" to AuthManager.csrf
        )
        val response = withContext(Dispatchers.IO) {
            JSONObject(
                Http.postForm(
                    "$API/x/member/web/update",
                    fields,
                    "https://space.bilibili.com/"
                )
            )
        }
        if (response.optInt("code", -1) != 0) {
            throw BiliException(response.optInt("code"), response.optString("message", "保存失败"))
        }
    }

    suspend fun userHomeCard(mid: Long): UserHomeCard? {
        val referer = "https://space.bilibili.com/$mid/"
        val data = runCatching {
            check(getJson("$API/x/web-interface/card?mid=$mid&photo=true", referer, desktop = true))
        }.getOrElse {
            runCatching {
                check(getJson("$API/x/web-interface/card?mid=$mid&photo=true", referer))
            }.getOrNull()
        } ?: return null
        val card = data.optJSONObject("card") ?: return null
        val space = data.optJSONObject("space")
        val banner = space?.stringOf("l_img").orEmpty()
            .ifBlank { space?.stringOf("s_img").orEmpty() }
        return UserHomeCard(
            mid = card.longOf("mid", mid),
            name = card.stringOf("name"),
            face = Http.normalizeUrl(card.stringOf("face")),
            sign = card.stringOf("sign"),
            banner = Http.normalizeUrl(banner),
            likes = data.longOf("like_num"),
            following = card.intOf("attention"),
            follower = data.intOf("fans").let { if (it > 0) it else data.intOf("follower") },
            archive = data.intOf("archive_count")
        )
    }

    suspend fun relationStat(mid: Long): RelationStat {
        val data = check(getJson("$API/x/relation/stat?vmid=$mid"))
        return RelationStat(
            following = data.intOf("following"),
            follower = data.intOf("follower"),
            whisper = data.intOf("whisper"),
            black = data.intOf("black")
        )
    }

    suspend fun upstatLikes(mid: Long): Int {
        val data = check(getJson("$API/x/space/upstat?mid=$mid"))
        return data.intOf("likes")
    }

    suspend fun userDynamics(mid: Long, offset: String): List<DynamicItem> {
        val data = check(
            getJson("$API/x/polymer/web-dynamic/v1/feed/space?host_mid=$mid&offset=$offset&page=1")
        )
        val array = data.optJSONArray("items") ?: return emptyList()
        val result = ArrayList<DynamicItem>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val modules = item.optJSONObject("modules") ?: continue
            val author = modules.optJSONObject("module_author")
            val dynamic = modules.optJSONObject("module_dynamic")
            val major = dynamic?.optJSONObject("major")
            val archive = major?.optJSONObject("archive")
            val draw = major?.optJSONObject("draw")
            val opus = major?.optJSONObject("opus")
            val opusPic = opus?.optJSONArray("pics")?.optJSONObject(0)
            val image = draw?.optJSONArray("items")?.optJSONObject(0)?.stringOf("src")
                ?: opusPic?.stringOf("url").orEmpty().ifBlank { opusPic?.stringOf("src").orEmpty() }
                ?: archive?.stringOf("cover").orEmpty()
            val faceRaw = author?.opt("face")
            val face = if (faceRaw is String) Http.normalizeUrl(faceRaw) else ""
            result.add(
                DynamicItem(
                    id = item.stringOf("id_str"),
                    type = item.stringOf("type"),
                    text = dynamic?.optJSONObject("desc")?.stringOf("text").orEmpty()
                        .ifBlank { opus?.optJSONObject("summary")?.stringOf("text").orEmpty() }
                        .ifBlank { opus?.stringOf("title").orEmpty() },
                    image = Http.normalizeUrl(image),
                    bvid = archive?.stringOf("bvid").orEmpty(),
                    title = archive?.stringOf("title").orEmpty(),
                    authorName = author?.stringOf("name").orEmpty(),
                    authorFace = face,
                    pubTs = author?.longOf("pub_ts") ?: 0L
                )
            )
        }
        return result
    }

    suspend fun historyList(): List<HistoryItem> {
        val data = check(
            getJson("$API/x/web-interface/history/cursor?max=0&view_at=0&business=archive&ps=30")
        )
        val array = data.optJSONArray("list") ?: return emptyList()
        val result = ArrayList<HistoryItem>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            result.add(
                HistoryItem(
                    bvid = item.stringOf("bvid"),
                    aid = item.longOf("kid"),
                    title = item.stringOf("title"),
                    cover = Http.normalizeUrl(item.stringOf("cover")),
                    authorName = item.stringOf("author_name"),
                    authorMid = item.longOf("author_mid"),
                    viewAt = item.longOf("view_at"),
                    progress = item.longOf("progress"),
                    duration = item.longOf("duration")
                )
            )
        }
        return result
    }

    suspend fun spaceInfo(mid: Long): SpaceInfo {
        val query = Wbi.sign(mapOf("mid" to mid.toString()))
        val data = check(getJson("$API/x/space/wbi/acc/info?$query"))
        return SpaceInfo(
            mid = data.longOf("mid", mid),
            name = data.stringOf("name"),
            face = Http.normalizeUrl(data.stringOf("face")),
            sign = data.stringOf("sign"),
            level = data.optJSONObject("level")?.intOf("current_level") ?: 0
        )
    }

    suspend fun spaceVideos(mid: Long, page: Int): List<VideoItem> {
        val referer = "https://space.bilibili.com/$mid/"
        val query = Wbi.sign(
            mapOf(
                "mid" to mid.toString(),
                "ps" to "30",
                "pn" to page.toString(),
                "order" to "pubdate"
            )
        )
        val data = runCatching {
            check(getJson("$API/x/space/wbi/arc/search?$query", referer, desktop = true))
        }.getOrElse { first ->
            // wbi 接口偶发风控(-352/-412)，退回旧接口
            Log.w("BiliFlow", "space wbi failed: ${first.message}")
            runCatching {
                check(getJson("$API/x/space/arc/search?mid=$mid&ps=30&pn=$page&order=pubdate", referer, desktop = true))
            }.getOrElse { second ->
                Log.w("BiliFlow", "space legacy failed: ${second.message}")
                runCatching {
                    check(getJson("$API/x/space/arc/search?mid=$mid&ps=30&pn=$page&order=pubdate", referer))
                }.getOrElse { third ->
                    Log.w("BiliFlow", "space mobile failed: ${third.message}")
                    throw third
                }
            }
        }
        val array = data.optJSONObject("list")?.optJSONArray("vlist") ?: return emptyList()
        val result = ArrayList<VideoItem>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val bvid = item.stringOf("bvid")
            if (bvid.isBlank()) continue
            result.add(
                VideoItem(
                    bvid = bvid,
                    aid = item.longOf("aid"),
                    title = item.stringOf("title"),
                    cover = Http.normalizeUrl(item.stringOf("pic")),
                    desc = item.stringOf("description").ifBlank { item.stringOf("title") },
                    ownerName = item.stringOf("author"),
                    ownerMid = item.longOf("mid", mid),
                    view = item.longOf("play"),
                    danmaku = item.longOf("video_review"),
                    reply = item.longOf("comment"),
                    durationSec = parseDuration(item.stringOf("length")),
                    pubdate = item.longOf("created")
                )
            )
        }
        return result
    }

    suspend fun favoriteFolders(): List<FavFolder> {
        val data = check(getJson("$API/x/v3/fav/folder/created/list-all?up_mid=${AuthManager.mid}"))
        val array = data.optJSONArray("list") ?: return emptyList()
        val result = ArrayList<FavFolder>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            result.add(
                FavFolder(
                    id = item.longOf("id"),
                    title = item.stringOf("title"),
                    count = item.longOf("media_count")
                )
            )
        }
        return result
    }

    suspend fun favoriteResources(mediaId: Long, page: Int): List<VideoItem> {
        val data = check(
            getJson("$API/x/v3/fav/resource/list?media_id=$mediaId&pn=$page&ps=20&platform=web")
        )
        val array = data.optJSONArray("medias") ?: return emptyList()
        val result = ArrayList<VideoItem>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val bvid = item.stringOf("bvid")
            if (bvid.isBlank()) continue
            val upper = item.optJSONObject("upper")
            val cnt = item.optJSONObject("cnt_info")
            result.add(
                VideoItem(
                    bvid = bvid,
                    aid = item.longOf("id"),
                    title = item.stringOf("title"),
                    cover = Http.normalizeUrl(item.stringOf("cover")),
                    desc = item.stringOf("intro").ifBlank { item.stringOf("title") },
                    ownerName = upper?.stringOf("name").orEmpty(),
                    ownerMid = upper?.longOf("mid") ?: 0L,
                    view = cnt?.longOf("play") ?: 0L,
                    danmaku = cnt?.longOf("danmaku") ?: 0L,
                    favorite = cnt?.longOf("collect") ?: 0L,
                    durationSec = item.intOf("duration")
                )
            )
        }
        return result
    }

    // ---------------------------------------------------------------- mall

    suspend fun ensureBuvid() {
        if (Http.cookie.contains("buvid3=")) return
        val response = runCatching {
            withContext(Dispatchers.IO) {
                JSONObject(
                    Http.get(
                        "$API/x/frontend/finger/spi",
                        "https://www.bilibili.com/"
                    )
                )
            }
        }.getOrNull() ?: return
        val data = response.optJSONObject("data") ?: return
        val b3 = data.stringOf("b_3")
        val b4 = data.stringOf("b_4")
        if (b3.isBlank()) return
        val parts = listOf(
            Http.cookie,
            "buvid3=$b3",
            "buvid4=$b4",
            "b_nut=${System.currentTimeMillis() / 1000L}"
        ).filter { it.isNotBlank() }
        Http.cookie = parts.joinToString("; ")
    }

    suspend fun mallFeed(feedType: Int, page: Int): List<MallItem> {
        val url = "https://mall.bilibili.com/mall-c-search/home/feed/list" +
            "?pageNum=$page&pageSize=20&mVersion=7&platform=h5&mobiApp=android&feedType=$feedType"
        val response = withContext(Dispatchers.IO) {
            JSONObject(Http.get(url, "https://mall.bilibili.com/"))
        }
        if (response.optInt("code", -1) != 0) {
            Log.w("BiliFlow", "mall feed code=${response.optInt("code")} msg=${response.optString("message")}")
            return emptyList()
        }
        val list = response.optJSONObject("data")
            ?.optJSONObject("vo")
            ?.optJSONArray("list")
            ?: return emptyList()
        val result = ArrayList<MallItem>(list.length())
        for (index in 0 until list.length()) {
            val card = list.optJSONObject(index) ?: continue
            if (card.stringOf("type") != "mallitems") continue
            parseMallItem(card)?.let(result::add)
        }
        return result
    }

    private fun parseMallItem(card: JSONObject): MallItem? {
        val title = card.stringOf("title").trim()
        if (title.isBlank()) return null
        val image = card.optJSONArray("imageUrls")?.optString(0).orEmpty()
        val priceDesc = card.optJSONArray("priceDesc")?.optString(0).orEmpty()
        val jump = card.optJSONArray("jumpUrls")?.optString(0).orEmpty()
        val frontTag = card.optJSONObject("feedTag")
            ?.optJSONArray("frontTag")
            ?.optJSONObject(0)
            ?.stringOf("title")
            .orEmpty()
        val tag = frontTag.ifBlank { card.stringOf("tagName") }
        var ratio = 1f
        val wh = card.stringOf("imgWH")
        if (wh.contains(":")) {
            val parts = wh.split(":")
            val w = parts.getOrNull(0)?.toFloatOrNull() ?: 0f
            val h = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
            if (w > 0f && h > 0f) ratio = (h / w).coerceIn(0.7f, 1.7f)
        }
        return MallItem(
            id = card.stringOf("id"),
            title = title,
            image = Http.normalizeUrl(image),
            priceText = priceDesc.ifBlank { card.optJSONArray("price")?.optString(0).orEmpty() },
            priceSymbol = card.stringOf("priceSymbol").ifBlank { "¥" },
            tag = tag,
            url = Http.normalizeUrl(jump),
            ratio = ratio
        )
    }

    // ---------------------------------------------------------------- internals

    private suspend fun dashPlayInfo(bvid: String, cid: Long, qn: Int): PlayInfo? {
        val data = check(
            getJson(
                "$API/x/player/playurl?bvid=$bvid&cid=$cid&qn=$qn" +
                    "&fnval=4048&fourk=1&platform=pc&high_quality=1"
            )
        )
        val dash = data.optJSONObject("dash") ?: return null
        val videoArray = dash.optJSONArray("video") ?: return null
        val videos = ArrayList<JSONObject>(videoArray.length())
        for (index in 0 until videoArray.length()) {
            videoArray.optJSONObject(index)?.let(videos::add)
        }
        if (videos.isEmpty()) return null

        val eligible = videos.filter { it.intOf("id") <= qn }.ifEmpty { videos }
        val bestQuality = eligible.maxOf { it.intOf("id") }
        val sameQuality = eligible.filter { it.intOf("id") == bestQuality }
        val video = sameQuality.firstOrNull { it.intOf("codecid") == 7 }
            ?: sameQuality.firstOrNull { it.intOf("codecid") == 12 }
            ?: sameQuality.firstOrNull { it.intOf("codecid") == 13 }
            ?: sameQuality.first()

        val audioArray = dash.optJSONArray("audio")
        val audios = ArrayList<JSONObject>()
        if (audioArray != null) {
            for (index in 0 until audioArray.length()) {
                audioArray.optJSONObject(index)?.let(audios::add)
            }
        }
        val audio = audios.filter { it.intOf("codecid") == 0 }
            .maxByOrNull { it.intOf("id") }
            ?: audios.maxByOrNull { it.intOf("id") }
            ?: return null

        val videoUrl = Http.normalizeUrl(
            video.stringOf("baseUrl").ifBlank { video.stringOf("base_url") }
        )
        val audioUrl = Http.normalizeUrl(
            audio.stringOf("baseUrl").ifBlank { audio.stringOf("base_url") }
        )
        if (videoUrl.isBlank() || audioUrl.isBlank()) return null
        val info = PlayInfo(
            urls = emptyList(),
            quality = video.intOf("id", data.intOf("quality", qn)),
            lengthMs = dash.longOf("duration") * 1000L,
            dashVideoUrl = videoUrl,
            dashAudioUrl = audioUrl
        )
        Log.d("BiliFlow", "play dash bvid=$bvid requested=$qn actual=${info.quality}")
        return info
    }

    private suspend fun getJson(
        url: String,
        referer: String = REFERER,
        desktop: Boolean = false
    ): JSONObject =
        withContext(Dispatchers.IO) {
            JSONObject(if (desktop) Http.getDesktop(url, referer) else Http.get(url, referer))
        }

    private suspend fun postAuth(
        path: String,
        fields: Map<String, String>,
        referer: String = REFERER
    ): JSONObject {
        if (!AuthManager.isLoggedIn || AuthManager.csrf.isBlank()) {
            throw BiliException(-101, "请先登录")
        }
        val all = fields.toMutableMap()
        all["csrf"] = AuthManager.csrf
        return withContext(Dispatchers.IO) {
            JSONObject(Http.postForm("$API$path", all, referer))
        }
    }

    private fun check(response: JSONObject): JSONObject {
        val code = response.optInt("code", -1)
        if (code != 0) {
            throw BiliException(code, response.optString("message", "请求失败"))
        }
        return response.optJSONObject("data") ?: JSONObject()
    }

    private fun isAdItem(item: JSONObject): Boolean {
        if (item.optJSONObject("ad_info") != null) return true
        if (item.optBoolean("is_ad") || item.optBoolean("is_advert")) return true
        val goto = item.stringOf("goto")
        if (goto == "ad" || goto == "banner" || goto == "live" || goto == "special") return true
        if (item.stringOf("card_goto") == "ad") return true
        if (item.stringOf("business_mark") == "ad") return true
        if (item.stringOf("card_type") == "ad") return true
        return false
    }

    private fun parseRecommend(item: JSONObject): VideoItem? {
        if (isAdItem(item)) return null
        val bvid = item.stringOf("bvid")
        val cid = item.longOf("cid")
        if (bvid.isBlank() || cid <= 0L) return null
        val owner = item.optJSONObject("owner")
        val stat = item.optJSONObject("stat")
        val title = item.stringOf("title").ifBlank { "未命名视频" }
        return VideoItem(
            bvid = bvid,
            aid = item.longOf("id").takeIf { it > 0L } ?: item.longOf("aid"),
            cid = cid,
            title = title,
            cover = Http.normalizeUrl(item.stringOf("pic")),
            desc = item.stringOf("desc").ifBlank { title },
            ownerName = owner?.stringOf("name").orEmpty().ifBlank { "未知 UP 主" },
            ownerFace = Http.normalizeUrl(owner?.stringOf("face").orEmpty()),
            ownerMid = owner?.longOf("mid") ?: 0L,
            view = stat?.longOf("view") ?: 0L,
            danmaku = stat?.longOf("danmaku") ?: 0L,
            like = stat?.longOf("like") ?: 0L,
            coin = stat?.longOf("coin") ?: 0L,
            reply = stat?.longOf("reply") ?: 0L,
            favorite = stat?.longOf("favorite") ?: 0L,
            share = stat?.longOf("share") ?: 0L,
            durationSec = item.intOf("duration"),
            pubdate = item.longOf("pubdate"),
            category = item.stringOf("tname"),
            reason = item.stringOf("rcmd_reason")
        )
    }

    private fun parsePopular(item: JSONObject): VideoItem? {
        val bvid = item.stringOf("bvid")
        val cid = item.longOf("cid")
        if (bvid.isBlank() || cid <= 0L) return null
        val owner = item.optJSONObject("owner")
        val stat = item.optJSONObject("stat")
        val title = item.stringOf("title").ifBlank { "未命名视频" }
        return VideoItem(
            bvid = bvid,
            aid = item.longOf("aid"),
            cid = cid,
            title = title,
            cover = Http.normalizeUrl(item.stringOf("pic")),
            desc = item.stringOf("desc").ifBlank { title },
            ownerName = owner?.stringOf("name").orEmpty().ifBlank { "未知 UP 主" },
            ownerFace = Http.normalizeUrl(owner?.stringOf("face").orEmpty()),
            ownerMid = owner?.longOf("mid") ?: 0L,
            view = stat?.longOf("view") ?: 0L,
            danmaku = stat?.longOf("danmaku") ?: 0L,
            like = stat?.longOf("like") ?: 0L,
            coin = stat?.longOf("coin") ?: 0L,
            reply = stat?.longOf("reply") ?: 0L,
            favorite = stat?.longOf("favorite") ?: 0L,
            share = stat?.longOf("share") ?: 0L,
            durationSec = item.intOf("duration"),
            pubdate = item.longOf("pubdate"),
            category = item.stringOf("tname"),
            reason = "热门"
        )
    }

    private fun parseSearch(item: JSONObject): VideoItem? {
        val bvid = item.stringOf("bvid")
        if (bvid.isBlank()) return null
        val title = stripHtml(item.stringOf("title")).ifBlank { "未命名视频" }
        return VideoItem(
            bvid = bvid,
            aid = item.longOf("aid").takeIf { it > 0L } ?: item.longOf("id"),
            cid = item.longOf("cid"),
            title = title,
            cover = Http.normalizeUrl(item.stringOf("pic")),
            desc = stripHtml(item.stringOf("description")).ifBlank { title },
            ownerName = stripHtml(item.stringOf("author")).ifBlank { "未知 UP 主" },
            ownerFace = Http.normalizeUrl(item.stringOf("upic")),
            ownerMid = item.longOf("mid"),
            view = item.longOf("play"),
            danmaku = item.longOf("danmaku"),
            like = 0L,
            reply = item.longOf("review"),
            favorite = item.longOf("favorites"),
            share = 0L,
            durationSec = parseDuration(item.stringOf("duration")),
            pubdate = item.longOf("pubdate"),
            category = item.stringOf("typename"),
            reason = "搜索"
        )
    }

    private fun parseDynamicVideo(item: JSONObject): VideoItem? {
        if (item.stringOf("type") == "DYNAMIC_TYPE_AD") return null
        val modules = item.optJSONObject("modules") ?: return null
        val tag = modules.optJSONObject("module_tag")?.stringOf("text").orEmpty()
        if (tag.contains("广告") || tag.contains("推广")) return null
        val dynamic = modules.optJSONObject("module_dynamic") ?: return null
        val major = dynamic.optJSONObject("major") ?: return null
        if (major.has("ad") || major.stringOf("type").equals("AD", ignoreCase = true)) return null
        val archive = major.optJSONObject("archive") ?: return null
        val bvid = archive.stringOf("bvid")
        if (bvid.isBlank()) return null
        val author = modules.optJSONObject("module_author")
        val stat = modules.optJSONObject("module_stat")
        val title = archive.stringOf("title").ifBlank { "动态视频" }
        val cover = Http.normalizeUrl(archive.stringOf("cover"))
        val like = stat?.optJSONObject("like")?.longOf("count")
            ?: archive.optJSONObject("stat")?.longOf("like")
            ?: 0L
        val reply = stat?.optJSONObject("comment")?.longOf("count")
            ?: archive.optJSONObject("stat")?.longOf("reply")
            ?: 0L
        val share = stat?.optJSONObject("forward")?.longOf("count") ?: 0L
        val dynamicStat = archive.optJSONObject("stat")
        return VideoItem(
            bvid = bvid,
            aid = archive.longOf("aid"),
            cid = 0L,
            title = title,
            cover = cover,
            desc = archive.stringOf("desc").ifBlank { title },
            ownerName = author?.stringOf("name").orEmpty().ifBlank { "未知 UP 主" },
            ownerFace = Http.normalizeUrl(author?.stringOf("face").orEmpty()),
            ownerMid = author?.longOf("mid") ?: 0L,
            view = dynamicStat?.longOf("play") ?: 0L,
            danmaku = dynamicStat?.longOf("danmaku") ?: 0L,
            like = like,
            reply = reply,
            favorite = 0L,
            share = share,
            durationSec = parseDuration(archive.stringOf("duration_text")),
            pubdate = author?.longOf("pub_ts") ?: 0L,
            category = "",
            reason = "关注动态"
        )
    }

    private fun parseDetail(data: JSONObject): VideoItem {
        val owner = data.optJSONObject("owner")
        val stat = data.optJSONObject("stat")
        val title = data.stringOf("title").ifBlank { "未命名视频" }
        return VideoItem(
            bvid = data.stringOf("bvid"),
            aid = data.longOf("aid"),
            cid = data.longOf("cid"),
            title = title,
            cover = Http.normalizeUrl(data.stringOf("pic")),
            desc = data.stringOf("desc").ifBlank { title },
            ownerName = owner?.stringOf("name").orEmpty().ifBlank { "未知 UP 主" },
            ownerFace = Http.normalizeUrl(owner?.stringOf("face").orEmpty()),
            ownerMid = owner?.longOf("mid") ?: 0L,
            view = stat?.longOf("view") ?: 0L,
            danmaku = stat?.longOf("danmaku") ?: 0L,
            like = stat?.longOf("like") ?: 0L,
            coin = stat?.longOf("coin") ?: 0L,
            reply = stat?.longOf("reply") ?: 0L,
            favorite = stat?.longOf("favorite") ?: 0L,
            share = stat?.longOf("share") ?: 0L,
            durationSec = data.intOf("duration"),
            pubdate = data.longOf("pubdate"),
            category = data.stringOf("tname"),
            reason = ""
        )
    }

    private fun parseComment(data: JSONObject, depth: Int = 0): CommentItem? {
        val member = data.optJSONObject("member") ?: return null
        val contentObj = data.optJSONObject("content")
        val content = contentObj?.stringOf("message").orEmpty()
        val emotes = parseCommentEmotes(contentObj?.optJSONObject("emote"))
        val pictures = parseCommentPictures(contentObj?.optJSONArray("pictures"))
        // 纯图片评论 message 为空，不能直接丢弃
        if (content.isBlank() && pictures.isEmpty()) return null
        val previewReplies = ArrayList<CommentItem>()
        if (depth < 1) {
            val replies = data.optJSONArray("replies")
            if (replies != null) {
                for (index in 0 until replies.length()) {
                    replies.optJSONObject(index)?.let { parseComment(it, depth + 1) }
                        ?.let(previewReplies::add)
                }
            }
        }
        return CommentItem(
            rpid = data.longOf("rpid"),
            root = data.longOf("root"),
            parent = data.longOf("parent"),
            userName = member.stringOf("uname").ifBlank { "B 站用户" },
            mid = member.longOf("mid"),
            avatar = Http.normalizeUrl(member.stringOf("avatar")),
            content = stripHtml(content),
            like = data.longOf("like"),
            liked = data.boolOf("liked"),
            ctime = data.longOf("ctime"),
            replyCount = data.longOf("rcount"),
            isUpTop = data.boolOf("is_up_top"),
            isUpLiked = data.boolOf("is_up_liked"),
            replies = previewReplies,
            emotes = emotes,
            pictures = pictures
        )
    }

    private fun parseCommentEmotes(obj: JSONObject?): Map<String, CommentEmote> {
        if (obj == null || obj.length() == 0) return emptyMap()
        val out = HashMap<String, CommentEmote>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val emote = obj.optJSONObject(key) ?: continue
            val url = Http.normalizeUrl(emote.stringOf("url"))
            if (url.isBlank()) continue
            val size = emote.optInt("size", emote.optJSONObject("meta")?.optInt("size") ?: 1)
            out[key] = CommentEmote(url = url, big = size == 2)
        }
        return out
    }

    private fun parseCommentPictures(array: JSONArray?): List<CommentPicture> {
        if (array == null || array.length() == 0) return emptyList()
        val out = ArrayList<CommentPicture>(array.length())
        for (index in 0 until array.length()) {
            val pic = array.optJSONObject(index) ?: continue
            val url = Http.normalizeUrl(pic.stringOf("img_src"))
            if (url.isBlank()) continue
            out.add(
                CommentPicture(
                    url = url,
                    width = pic.optInt("img_width"),
                    height = pic.optInt("img_height")
                )
            )
        }
        return out
    }

    private fun parseDanmakuXml(bytes: ByteArray): List<DanmakuItem> {
        val result = ArrayList<DanmakuItem>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "d") {
                val p = parser.getAttributeValue(null, "p").orEmpty()
                val text = parser.nextText().orEmpty().trim()
                val parts = p.split(",")
                if (text.isNotBlank() && parts.size >= 4) {
                    val seconds = parts[0].toDoubleOrNull() ?: 0.0
                    val mode = parts[1].toIntOrNull() ?: 1
                    val size = parts[2].toIntOrNull() ?: 25
                    val color = parts[3].toIntOrNull() ?: 0xFFFFFF
                    result.add(
                        DanmakuItem(
                            timeMs = (seconds * 1000).toLong(),
                            mode = mode,
                            fontSize = size,
                            color = color,
                            text = text.take(100)
                        )
                    )
                }
            }
            event = parser.next()
        }
        return result.sortedBy { it.timeMs }
    }

    private fun startsWithAscii(bytes: ByteArray, prefix: String): Boolean {
        if (bytes.size < prefix.length) return false
        for (index in prefix.indices) {
            if (bytes[index] != prefix[index].code.toByte()) return false
        }
        return true
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater(true)
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 4)
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    out.write(buffer, 0, count)
                }
            }
            out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private fun stripHtml(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
        }.getOrDefault(value).trim()
    }

    private fun parseDuration(value: String): Int {
        if (value.isBlank()) return 0
        val parts = value.split(":")
        return runCatching {
            when (parts.size) {
                1 -> parts[0].toInt()
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                else -> 0
            }
        }.getOrDefault(0)
    }

    private fun JSONObject.stringOf(name: String): String {
        val value = opt(name)
        return if (value == null || value === JSONObject.NULL) "" else value.toString()
    }

    private fun JSONObject.intOf(name: String, fallback: Int = 0): Int {
        val value = opt(name)
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: fallback
            else -> fallback
        }
    }

    private fun JSONObject.longOf(name: String, fallback: Long = 0L): Long {
        val value = opt(name)
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: fallback
            else -> fallback
        }
    }

    private fun JSONObject.boolOf(name: String, fallback: Boolean = false): Boolean {
        val value = opt(name)
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value == "true" || value == "1"
            else -> fallback
        }
    }

    private data class CachedDanmaku(
        val createdAt: Long,
        val items: List<DanmakuItem>
    )
}


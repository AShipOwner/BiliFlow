package com.biliflow.app.data

enum class FeedSource {
    FOLLOW,
    RECOMMEND,
    POPULAR
}

enum class QualityMode(val label: String, val qn: Int?) {
    AUTO("自动", null),
    Q1080("1080P", 80),
    Q720("720P", 64),
    Q480("480P", 32),
    Q360("360P", 16)
}

data class VideoItem(
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val cover: String = "",
    val desc: String = "",
    val ownerName: String = "",
    val ownerFace: String = "",
    val ownerMid: Long = 0,
    val view: Long = 0,
    val danmaku: Long = 0,
    val like: Long = 0,
    val coin: Long = 0,
    val reply: Long = 0,
    val favorite: Long = 0,
    val share: Long = 0,
    val durationSec: Int = 0,
    val pubdate: Long = 0,
    val category: String = "",
    val reason: String = ""
) {
    val key: String get() = bvid.ifBlank { aid.toString() }
    val webUrl: String get() = "https://www.bilibili.com/video/$bvid"
}

data class CommentItem(
    val rpid: Long = 0,
    val root: Long = 0,
    val parent: Long = 0,
    val userName: String = "",
    val mid: Long = 0,
    val avatar: String = "",
    val content: String = "",
    val like: Long = 0,
    val liked: Boolean = false,
    val ctime: Long = 0,
    val replyCount: Long = 0,
    val isUpTop: Boolean = false,
    val isUpLiked: Boolean = false,
    val replies: List<CommentItem> = emptyList(),
    val emotes: Map<String, CommentEmote> = emptyMap(),
    val pictures: List<CommentPicture> = emptyList()
) {
    val id: Long get() = rpid
}

/** B 站评论区表情：message 里的 [key] 占位符 → 图片地址 */
data class CommentEmote(
    val url: String = "",
    val big: Boolean = false
)

/** 图片评论 */
data class CommentPicture(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0
)

data class CommentPage(
    val items: List<CommentItem> = emptyList(),
    val topItems: List<CommentItem> = emptyList(),
    val total: Long = 0,
    val hasMore: Boolean = false
)

data class DanmakuItem(
    val timeMs: Long,
    val mode: Int,
    val fontSize: Int,
    val color: Int,
    val text: String
)

data class SocialState(
    val liked: Boolean = false,
    val coinCount: Int = 0,
    val favorite: Boolean = false,
    val following: Boolean = false,
    val likeDelta: Long = 0,
    val favoriteDelta: Long = 0,
    val coinDelta: Int = 0,
    val loaded: Boolean = false,
    val loading: Boolean = false
)

data class AccountUser(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val level: Int = 0,
    val vipStatus: Int = 0,
    val vipType: Int = 0,
    val vipLabel: String = "",
    val coins: Int = 0,
    val following: Int = 0,
    val follower: Int = 0
) {
    val isVip: Boolean get() = vipStatus == 1
}

data class NetworkProfile(
    val online: Boolean = true,
    val wifi: Boolean = true,
    val cellular: Boolean = false,
    val downKbps: Int = 0,
    val metered: Boolean = false
)

data class FollowPage(
    val items: List<VideoItem> = emptyList(),
    val nextOffset: String = "",
    val hasMore: Boolean = false
)

data class PlayInfo(
    val urls: List<String> = emptyList(),
    val quality: Int = 0,
    val lengthMs: Long = 0,
    val dashVideoUrl: String? = null,
    val dashAudioUrl: String? = null
) {
    val isDash: Boolean get() = dashVideoUrl != null && dashAudioUrl != null
}

data class LiveRoom(
    val roomId: Long = 0,
    val title: String = "",
    val uname: String = "",
    val face: String = "",
    val cover: String = "",
    val online: Long = 0,
    val area: String = "",
    val mid: Long = 0
)

data class MessageItem(
    val id: String = "",
    val nickname: String = "",
    val face: String = "",
    val content: String = "",
    val title: String = "",
    val time: Long = 0,
    val isNew: Boolean = false,
    val uri: String = "",
    val mid: Long = 0
)

data class FollowUser(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val mutual: Boolean = false
)

data class FanItem(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val sign: String = ""
)

data class WhisperSession(
    val talkerId: Long = 0,
    val name: String = "",
    val face: String = "",
    val lastMsg: String = "",
    val unread: Int = 0,
    val ts: Long = 0
)

data class ChatMessage(
    val senderUid: Long = 0,
    val content: String = "",
    val ts: Long = 0,
    val isMine: Boolean = false
)

data class RelationStat(
    val following: Int = 0,
    val follower: Int = 0,
    val whisper: Int = 0,
    val black: Int = 0
)

data class DynamicItem(
    val id: String = "",
    val type: String = "",
    val text: String = "",
    val image: String = "",
    val bvid: String = "",
    val title: String = "",
    val authorName: String = "",
    val authorFace: String = "",
    val pubTs: Long = 0
)

data class HistoryItem(
    val bvid: String = "",
    val aid: Long = 0,
    val title: String = "",
    val cover: String = "",
    val authorName: String = "",
    val authorMid: Long = 0,
    val viewAt: Long = 0,
    val progress: Long = 0,
    val duration: Long = 0
)

data class UserHomeCard(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val sign: String = "",
    val banner: String = "",
    val likes: Long = 0,
    val following: Int = 0,
    val follower: Int = 0,
    val archive: Int = 0
)

data class SpaceInfo(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val sign: String = "",
    val level: Int = 0
)

data class FavFolder(
    val id: Long = 0,
    val title: String = "",
    val count: Long = 0
)

data class MallItem(
    val id: String = "",
    val title: String = "",
    val image: String = "",
    val priceText: String = "",
    val priceSymbol: String = "¥",
    val tag: String = "",
    val url: String = "",
    val ratio: Float = 1f
)

fun effectiveQualityQn(
    mode: QualityMode,
    vip: Boolean,
    network: NetworkProfile
): Int {
    val manual = mode.qn
    if (manual != null) {
        return if (manual > 64 && !vip) 64 else manual
    }
    if (!network.online) return 64
    if (network.wifi) {
        return when {
            network.downKbps <= 0 -> 64
            vip && network.downKbps >= 6000 -> 80
            network.downKbps >= 2500 -> 64
            else -> 32
        }
    }
    if (network.cellular) {
        return when {
            vip && network.downKbps >= 12000 -> 64
            network.downKbps >= 3000 -> 32
            else -> 16
        }
    }
    return 64
}

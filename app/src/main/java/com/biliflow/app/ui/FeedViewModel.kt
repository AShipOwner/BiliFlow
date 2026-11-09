package com.biliflow.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliflow.app.data.AuthManager
import com.biliflow.app.data.AuthState
import com.biliflow.app.data.BiliApi
import com.biliflow.app.data.FeedSource
import com.biliflow.app.data.LoginPrompt
import com.biliflow.app.data.PlayInfo
import com.biliflow.app.data.SocialState
import com.biliflow.app.data.UiMessages
import com.biliflow.app.data.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class FeedState(
    val items: List<VideoItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
    val cursor: String = "",
    val end: Boolean = false,
    val index: Int = 0
)

sealed interface PlayResult {
    data class Success(val info: PlayInfo) : PlayResult
    data class Error(val message: String) : PlayResult
}

class FeedViewModel : ViewModel() {
    private val followState = MutableStateFlow(FeedState())
    private val recommendState = MutableStateFlow(FeedState())
    private val popularState = MutableStateFlow(FeedState())
    private val followPublic = followState.asStateFlow()
    private val recommendPublic = recommendState.asStateFlow()
    private val popularPublic = popularState.asStateFlow()

    private val detailCache = ConcurrentHashMap<String, VideoItem>()
    private val playCache = ConcurrentHashMap<String, CachedPlay>()
    private val _social = MutableStateFlow<Map<String, SocialState>>(emptyMap())
    val social: StateFlow<Map<String, SocialState>> = _social.asStateFlow()
    private val socialLoading = ConcurrentHashMap.newKeySet<String>()
    private val prefetching = ConcurrentHashMap.newKeySet<String>()

    init {
        viewModelScope.launch {
            AuthManager.state.collect { state ->
                if (state !is AuthState.LoggedIn) {
                    _social.value = emptyMap()
                    socialLoading.clear()
                    followState.value = FeedState()
                }
            }
        }
    }

    fun state(source: FeedSource): StateFlow<FeedState> = when (source) {
        FeedSource.FOLLOW -> followPublic
        FeedSource.RECOMMEND -> recommendPublic
        FeedSource.POPULAR -> popularPublic
    }

    fun ensureLoaded(source: FeedSource) {
        val current = mutableState(source).value
        if (current.items.isEmpty() && !current.loading && current.error == null) {
            if (source == FeedSource.FOLLOW && !AuthManager.isLoggedIn) return
            load(source)
        }
    }

    fun retry(source: FeedSource) {
        mutableState(source).update { it.copy(error = null) }
        load(source)
    }

    fun loadMore(source: FeedSource) {
        val flow = mutableState(source)
        val current = flow.value
        if (current.loading || current.loadingMore || current.end || current.items.isEmpty()) return
        if (source == FeedSource.FOLLOW && !AuthManager.isLoggedIn) return
        val nextPage = current.page + 1
        flow.update { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch {
            try {
                if (source == FeedSource.FOLLOW) {
                    val page = BiliApi.followFeed(current.cursor)
                    flow.update { state ->
                        state.copy(
                            items = merge(state.items, page.items),
                            loadingMore = false,
                            page = nextPage,
                            cursor = page.nextOffset,
                            end = !page.hasMore
                        )
                    }
                } else {
                    var attempt = nextPage
                    var items = fetch(source, attempt)
                    // 推荐流偶尔会返回整页重复，跳过重复页继续取，避免信息流“卡住”
                    while (items.isNotEmpty() &&
                        merge(current.items, items).size == current.items.size &&
                        attempt < nextPage + 2
                    ) {
                        attempt += 1
                        items = fetch(source, attempt)
                    }
                    val pageIndex = attempt
                    flow.update { state ->
                        state.copy(
                            items = merge(state.items, items),
                            loadingMore = false,
                            page = pageIndex,
                            end = items.isEmpty()
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                flow.update {
                    it.copy(loadingMore = false, error = error.message ?: "加载失败")
                }
            }
        }
    }

    fun setIndex(source: FeedSource, index: Int) {
        mutableState(source).update { if (it.index == index) it else it.copy(index = index) }
    }

    /**
     * 预取后面几条视频的播放地址，滑动到该条时省掉一次网络往返。
     */
    fun prefetchAround(items: List<VideoItem>, index: Int, qn: Int, forceProgressive: Boolean = false) {
        listOf(index + 1, index + 2, index + 3).forEach { pos ->
            val item = items.getOrNull(pos) ?: return@forEach
            if (item.bvid.isBlank()) return@forEach
            val key = "${item.bvid}|$qn|$forceProgressive"
            if (playCache[key] != null) return@forEach
            if (!prefetching.add(item.bvid)) return@forEach
            viewModelScope.launch {
                try {
                    val resolved = if (item.cid > 0L) item else detail(item) ?: return@launch
                    if (resolved.cid <= 0L) return@launch
                    val info = BiliApi.playUrl(resolved.bvid, resolved.cid, qn, forceProgressive)
                    if (playCache.size > 60) playCache.clear()
                    playCache[key] = CachedPlay(System.currentTimeMillis(), info)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // 预取失败不影响正式播放
                } finally {
                    prefetching.remove(item.bvid)
                }
            }
        }
    }

    suspend fun detail(item: VideoItem): VideoItem? {
        if (item.bvid.isBlank()) return null
        detailCache[item.bvid]?.let { return it }
        return try {
            val fresh = BiliApi.detail(item.bvid)
            detailCache[item.bvid] = fresh
            mergeDetail(fresh)
            fresh
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    suspend fun playback(
        item: VideoItem,
        qn: Int,
        forceProgressive: Boolean = false
    ): PlayResult {
        val cacheKey = "${item.bvid}|$qn|$forceProgressive"
        playCache[cacheKey]
            ?.takeIf { System.currentTimeMillis() - it.createdAt < 8 * 60 * 1000L }
            ?.let { return PlayResult.Success(it.info) }

        val resolved = if (item.cid > 0L) item else detail(item)
            ?: return PlayResult.Error("无法获取视频信息")
        if (resolved.cid <= 0L) return PlayResult.Error("无法获取视频 CID")

        return try {
            val info = BiliApi.playUrl(resolved.bvid, resolved.cid, qn, forceProgressive)
            if (playCache.size > 60) playCache.clear()
            playCache[cacheKey] = CachedPlay(System.currentTimeMillis(), info)
            PlayResult.Success(info)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            PlayResult.Error(error.message ?: "播放地址获取失败")
        }
    }

    fun socialOf(bvid: String): SocialState = _social.value[bvid] ?: SocialState()

    fun ensureSocial(item: VideoItem) {
        if (!AuthManager.isLoggedIn || item.bvid.isBlank()) return
        val current = _social.value[item.bvid]
        if (current?.loaded == true || socialLoading.contains(item.bvid)) return
        socialLoading.add(item.bvid)
        updateSocial(item.bvid) { (it ?: SocialState()).copy(loading = true) }
        viewModelScope.launch {
            try {
                val state = BiliApi.relationState(item.bvid)
                updateSocial(item.bvid) { state }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                updateSocial(item.bvid) { (it ?: SocialState()).copy(loading = false) }
                if (isLoginError(error)) LoginPrompt.show()
            } finally {
                socialLoading.remove(item.bvid)
            }
        }
    }

    fun toggleLike(item: VideoItem) {
        if (!AuthManager.ensureLogin()) return
        val current = socialOf(item.bvid)
        if (current.loading) return
        val target = !current.liked
        val optimistic = current.copy(
            liked = target,
            likeDelta = current.likeDelta + if (target) 1L else -1L,
            loading = true
        )
        updateSocial(item.bvid) { optimistic }
        viewModelScope.launch {
            try {
                BiliApi.toggleLike(item, target)
                updateSocial(item.bvid) {
                    optimistic.copy(loading = false, loaded = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                updateSocial(item.bvid) { current }
                handleActionError(error, if (target) "点赞失败" else "取消点赞失败")
            }
        }
    }

    fun addCoin(item: VideoItem, count: Int, alsoLike: Boolean) {
        if (!AuthManager.ensureLogin()) return
        val current = socialOf(item.bvid)
        val already = current.coinCount + current.coinDelta
        val actualCount = count.coerceAtMost(2 - already)
        if (actualCount <= 0) {
            UiMessages.show("该视频已投满 2 枚硬币")
            return
        }
        val likeAdded = alsoLike && !current.liked
        val optimistic = current.copy(
            coinDelta = current.coinDelta + actualCount,
            liked = if (alsoLike) true else current.liked,
            likeDelta = current.likeDelta + if (likeAdded) 1L else 0L,
            loading = true
        )
        updateSocial(item.bvid) { optimistic }
        viewModelScope.launch {
            try {
                BiliApi.addCoin(item, actualCount, alsoLike)
                updateSocial(item.bvid) { optimistic.copy(loading = false, loaded = true) }
                UiMessages.show("投币成功")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                updateSocial(item.bvid) { current }
                handleActionError(error, "投币失败")
            }
        }
    }

    fun toggleFavorite(item: VideoItem) {
        if (!AuthManager.ensureLogin()) return
        val current = socialOf(item.bvid)
        if (current.loading) return
        val target = !current.favorite
        val optimistic = current.copy(
            favorite = target,
            favoriteDelta = current.favoriteDelta + if (target) 1L else -1L,
            loading = true
        )
        updateSocial(item.bvid) { optimistic }
        viewModelScope.launch {
            try {
                BiliApi.setFavorite(item, target)
                updateSocial(item.bvid) {
                    optimistic.copy(loading = false, loaded = true)
                }
                UiMessages.show(if (target) "已收藏" else "已取消收藏")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                updateSocial(item.bvid) { current }
                handleActionError(error, if (target) "收藏失败" else "取消收藏失败")
            }
        }
    }

    fun toggleFollow(item: VideoItem) {
        if (!AuthManager.ensureLogin()) return
        if (item.ownerMid <= 0L) return
        val current = socialOf(item.bvid)
        if (current.loading) return
        val target = !current.following
        val optimistic = current.copy(following = target, loading = true)
        updateSocial(item.bvid) { optimistic }
        viewModelScope.launch {
            try {
                BiliApi.followUser(item.ownerMid, target)
                setFollowingForOwner(item.ownerMid, target, item.bvid)
                updateSocial(item.bvid) {
                    optimistic.copy(loading = false, loaded = true)
                }
                UiMessages.show(if (target) "已关注" else "已取消关注")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                updateSocial(item.bvid) { current }
                handleActionError(error, if (target) "关注失败" else "取消关注失败")
            }
        }
    }

    private fun setFollowingForOwner(mid: Long, following: Boolean, currentBvid: String) {
        val bvids = (followState.value.items + recommendState.value.items + popularState.value.items)
            .filter { it.ownerMid == mid }
            .mapTo(HashSet()) { it.bvid }
        bvids.add(currentBvid)
        _social.update { map ->
            map.mapValues { (bvid, state) ->
                if (bvid in bvids) state.copy(following = following, loading = false) else state
            }
        }
    }

    private fun updateSocial(bvid: String, transform: (SocialState?) -> SocialState) {
        _social.update { map ->
            val next = map.toMutableMap()
            next[bvid] = transform(next[bvid])
            next
        }
    }

    private fun handleActionError(error: Exception, fallback: String) {
        if (isLoginError(error)) {
            LoginPrompt.show()
            UiMessages.show("登录状态已失效，请重新登录")
            return
        }
        UiMessages.show(error.message?.takeIf { it.isNotBlank() } ?: fallback)
    }

    private fun isLoginError(error: Exception): Boolean =
        error is BiliApi.BiliException && error.code == -101

    private fun load(source: FeedSource) {
        val flow = mutableState(source)
        val current = flow.value
        if (current.loading || current.loadingMore) return
        if (source == FeedSource.FOLLOW && !AuthManager.isLoggedIn) {
            flow.update { it.copy(error = "请先登录", loading = false) }
            return
        }
        flow.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                if (source == FeedSource.FOLLOW) {
                    val page = BiliApi.followFeed(null)
                    flow.update { state ->
                        state.copy(
                            items = merge(state.items, page.items),
                            loading = false,
                            page = 1,
                            cursor = page.nextOffset,
                            error = null,
                            end = !page.hasMore
                        )
                    }
                } else {
                    val items = fetch(source, 1)
                    flow.update { state ->
                        state.copy(
                            items = merge(state.items, items),
                            loading = false,
                            page = 1,
                            error = null,
                            end = items.isEmpty()
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                flow.update {
                    it.copy(loading = false, error = error.message ?: "加载失败")
                }
            }
        }
    }

    private suspend fun fetch(source: FeedSource, page: Int): List<VideoItem> = when (source) {
        FeedSource.RECOMMEND -> BiliApi.recommend(page)
        FeedSource.POPULAR -> BiliApi.popular(page)
        FeedSource.FOLLOW -> BiliApi.followFeed(null).items
    }

    private fun mutableState(source: FeedSource): MutableStateFlow<FeedState> = when (source) {
        FeedSource.FOLLOW -> followState
        FeedSource.RECOMMEND -> recommendState
        FeedSource.POPULAR -> popularState
    }

    private fun merge(existing: List<VideoItem>, incoming: List<VideoItem>): List<VideoItem> {
        if (existing.isEmpty()) return incoming.distinctBy { it.key }
        val seen = existing.mapTo(HashSet()) { it.key }
        val extra = incoming.filter { seen.add(it.key) }
        return if (extra.isEmpty()) existing else existing + extra
    }

    private fun mergeDetail(fresh: VideoItem) {
        followState.update { state ->
            state.copy(items = state.items.map { if (it.bvid == fresh.bvid) fresh else it })
        }
        recommendState.update { state ->
            state.copy(items = state.items.map { if (it.bvid == fresh.bvid) fresh else it })
        }
        popularState.update { state ->
            state.copy(items = state.items.map { if (it.bvid == fresh.bvid) fresh else it })
        }
    }

    private data class CachedPlay(val createdAt: Long, val info: PlayInfo)
}

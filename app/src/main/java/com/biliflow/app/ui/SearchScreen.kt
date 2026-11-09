package com.biliflow.app.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliflow.app.data.BiliApi
import com.biliflow.app.data.LocalStore
import com.biliflow.app.data.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(onOpenVideo: (VideoItem) -> Unit, onBack: () -> Unit = {}) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    BackHandler { onBack() }

    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var loadingMore by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(false) }
    var searchToken by remember { mutableIntStateOf(0) }
    val history by LocalStore.searchHistory.collectAsStateCompat()

    LaunchedEffect(searchToken) {
        if (submitted.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        results = emptyList()
        page = 1
        end = false
        try {
            results = BiliApi.search(submitted, 1)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            Log.e("BiliFlow", "search failed for $submitted", exception)
            error = exception.message?.takeIf { it.isNotBlank() }
                ?: "搜索失败（${exception::class.java.simpleName}）"
        } finally {
            loading = false
        }
    }

    fun submit(text: String) {
        val value = text.trim()
        if (value.isBlank()) return
        query = value
        submitted = value
        LocalStore.addSearchHistory(value)
        focusManager.clearFocus()
        keyboard?.hide()
        searchToken++
    }

    fun loadMore() {
        if (loading || loadingMore || end) return
        loadingMore = true
        scope.launch {
            try {
                val nextPage = page + 1
                val more = BiliApi.search(submitted, nextPage)
                val seen = results.mapTo(HashSet()) { it.key }
                results = results + more.filter { seen.add(it.key) }
                page = nextPage
                end = more.isEmpty()
            } catch (_: Exception) {
            } finally {
                loadingMore = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackground())
            .screenEnter()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 58.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .pressClickable(pressedScale = 0.85f) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = appText(),
                    modifier = Modifier.size(22.dp)
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(appSurface())
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = appTextMuted(),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(color = appText(), fontSize = 14.sp),
                    cursorBrush = SolidColor(BiliAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "搜索 B 站视频",
                                    color = appTextMuted(),
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清除",
                        tint = appTextMuted(),
                        modifier = Modifier
                            .size(18.dp)
                            .noRippleClickable { query = "" }
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "搜索",
                color = if (query.isBlank()) appTextMuted() else appText(),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.noRippleClickable { submit(query) }
            )
        }

        when {
            loading -> {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    SearchSkeleton()
                }
            }

            error != null -> {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("搜索失败", color = appText(), fontSize = 15.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = error.orEmpty(),
                        color = appTextMuted(),
                        fontSize = 11.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    TextButton(onClick = { searchToken++ }) {
                        Text("重试", color = BiliAccent)
                    }
                }
            }

            results.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(results, key = { it.key }) { item ->
                        SearchResultRow(
                            item = item,
                            modifier = Modifier.animateItem(),
                            onClick = { onOpenVideo(item) }
                        )
                    }
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                loadingMore -> CircularProgressIndicator(
                                    color = appTextMuted(),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )

                                end -> Text(
                                    text = "没有更多了",
                                    color = appTextMuted(),
                                    fontSize = 11.sp
                                )

                                else -> TextButton(onClick = { loadMore() }) {
                                    Text("加载更多", color = appTextMuted(), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            submitted.isNotBlank() -> {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "没有找到「$submitted」相关视频",
                        color = appTextMuted(),
                        fontSize = 13.sp
                    )
                }
            }

            else -> SearchLanding(
                history = history,
                onPick = { value ->
                    query = value
                    submit(value)
                }
            )
        }
    }
}

@Composable
private fun SearchLanding(history: List<String>, onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = "随便看看",
            color = appText(),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("猫", "音乐", "原神", "纪录片").forEach { word ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(appSurface())
                        .pressClickable(pressedScale = 0.92f) { onPick(word) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(text = word, color = appText().copy(alpha = 0.9f), fontSize = 12.sp)
                }
            }
        }

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "搜索历史",
                    color = appText(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "清除",
                    color = appTextMuted(),
                    fontSize = 12.sp,
                    modifier = Modifier.noRippleClickable {
                        LocalStore.clearSearchHistory()
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
            history.forEach { word ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable { onPick(word) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = appTextMuted(),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = word,
                        color = appText().copy(alpha = 0.82f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(30.dp))
        Text(
            text = "数据来自哔哩哔哩公开 Web 接口\n登录、会员内容与互动功能未包含在本客户端中",
            color = appTextMuted(),
            fontSize = 11.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun SearchSkeleton() {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        repeat(6) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                SkeletonBox(Modifier.width(148.dp).height(83.dp), RoundedCornerShape(8.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBox(Modifier.fillMaxWidth().height(14.dp))
                    Spacer(Modifier.height(6.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.7f).height(14.dp))
                    Spacer(Modifier.height(8.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.4f).height(11.dp))
                    Spacer(Modifier.height(6.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.55f).height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    item: VideoItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressClickable(pressedScale = 0.98f) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Box(
            Modifier
                .width(148.dp)
                .height(83.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            RemoteImage(
                url = item.cover,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                targetWidth = 480
            )
            Text(
                text = formatDuration(item.durationSec),
                color = appText(),
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = appText(),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = item.ownerName,
                color = appTextMuted(),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatCount(item.view)} 播放 · ${formatCount(item.danmaku)} 弹幕",
                color = appText().copy(alpha = 0.45f),
                fontSize = 10.sp
            )
        }
    }
}

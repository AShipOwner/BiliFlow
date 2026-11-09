package com.biliflow.app.ui

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliflow.app.data.BiliApi
import com.biliflow.app.data.MallItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val SEARCH_PAGES_PER_CHANNEL = 3

private data class MallChannel(val label: String, val feedType: Int)

private val MALL_CHANNELS = listOf(
    MallChannel("推荐", 0),
    MallChannel("精选", 2),
    MallChannel("发现", 3)
)

private data class MallEntry(val icon: ImageVector, val label: String, val target: String)

private val MALL_SERVICES = listOf(
    MallEntry(Icons.Filled.ShoppingCart, "我的订单", "https://mall.bilibili.com/order"),
    MallEntry(Icons.Filled.AccountBalanceWallet, "充值中心", "https://pay.bilibili.com/"),
    MallEntry(Icons.Filled.LocalOffer, "券红包", "https://mall.bilibili.com/"),
    MallEntry(Icons.Filled.Star, "评价中心", "https://mall.bilibili.com/"),
    MallEntry(Icons.Filled.SupportAgent, "客服", "https://mall.bilibili.com/")
)

private val MALL_CATEGORIES = listOf(
    MallEntry(Icons.Filled.TheaterComedy, "演出", "演出"),
    MallEntry(Icons.Filled.Toys, "手办", "手办"),
    MallEntry(Icons.Filled.Category, "周边", "周边"),
    MallEntry(Icons.Filled.MenuBook, "漫画", "漫画"),
    MallEntry(Icons.Filled.Movie, "影视", "影视")
)

private fun mallSearchUrl(keyword: String): String =
    "https://mall.bilibili.com/search?keyword=" + Uri.encode(keyword)

@Composable
fun MallScreen() {
    MallHome()
}

@Composable
private fun MallHome() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var channel by rememberSaveable { mutableIntStateOf(0) }
    var feedType by rememberSaveable { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    var products by remember { mutableStateOf<List<MallItem>>(emptyList()) }
    var page by remember { mutableIntStateOf(2) }
    var loading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<MallItem?>(null) }
    val gridState = rememberLazyGridState()

    val searching = keyword.isNotBlank()

    LaunchedEffect(feedType, reloadKey, keyword) {
        products = emptyList()
        page = 2
        endReached = false
        failed = false
        loading = true
        if (searching) {
            val key = keyword.lowercase()
            val all = mutableListOf<MallItem>()
            for (type in listOf(0, 2, 3)) {
                for (index in 1..SEARCH_PAGES_PER_CHANNEL) {
                    val chunk = runCatching { BiliApi.mallFeed(type, index) }.getOrNull() ?: break
                    if (chunk.isEmpty()) break
                    all += chunk
                }
            }
            val hits = all.distinctBy { it.id }.filter {
                it.title.lowercase().contains(key) || it.tag.lowercase().contains(key)
            }
            products = hits
            endReached = true
            failed = all.isEmpty()
        } else {
            val list = runCatching { BiliApi.mallFeed(feedType, 1) }.getOrNull()
            if (list == null) {
                failed = true
            } else {
                products = list
                endReached = list.isEmpty()
            }
        }
        loading = false
    }

    suspend fun loadNext() {
        if (searching || loading || endReached || failed) return
        loading = true
        val next = page
        val chunk = runCatching { BiliApi.mallFeed(feedType, next) }.getOrNull()
        if (chunk == null) {
            failed = true
        } else {
            val merged = (products + chunk).distinctBy { it.id }
            if (chunk.isEmpty() || merged.size == products.size) endReached = true else page = next + 1
            products = merged
        }
        loading = false
    }

    LaunchedEffect(gridState, feedType, keyword) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { index ->
                if (products.isNotEmpty() && index >= products.size - 4) loadNext()
            }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(appBackground())
            .statusBarsPadding()
            .padding(top = 50.dp, bottom = 54.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(appField())
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = appTextMuted(),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(color = appText(), fontSize = 13.sp),
                    cursorBrush = SolidColor(BiliAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyword = query.trim()
                        channel = -1
                    }),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text("搜索会员购好物", color = appTextMuted(), fontSize = 13.sp)
                            }
                            inner()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "清除",
                        tint = appTextMuted(),
                        modifier = Modifier
                            .size(15.dp)
                            .noRippleClickable {
                                query = ""
                                keyword = ""
                                channel = 0
                                feedType = 0
                            }
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "搜索",
                color = appText(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.noRippleClickable {
                    keyword = query.trim()
                    channel = -1
                }
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MALL_CHANNELS.forEachIndexed { index, item ->
                val selected = !searching && channel == index
                val chipBackground by animateColorAsState(
                    targetValue = if (selected) appText() else Color(0xFF1F1F23),
                    animationSpec = tween(200),
                    label = "mallChipBackground"
                )
                val chipTextColor by animateColorAsState(
                    targetValue = if (selected) Color.Black else appTextMuted(),
                    animationSpec = tween(200),
                    label = "mallChipText"
                )
                Text(
                    text = item.label,
                    color = chipTextColor,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(chipBackground)
                        .pressClickable(pressedScale = 0.92f) {
                            keyword = ""
                            channel = index
                            feedType = item.feedType
                        }
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (searching) {
                Text(
                    text = "退出搜索",
                    color = BiliAccent,
                    fontSize = 12.sp,
                    modifier = Modifier.noRippleClickable {
                        keyword = ""
                        query = ""
                        channel = 0
                        feedType = 0
                    }
                )
            } else {
                Text(
                    text = "会员购",
                    color = BiliAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.noRippleClickable { openUrl(context, mallSearchUrl("手办")) }
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MALL_SERVICES.forEach { entry ->
                MallEntryCell(entry, appText()) { openUrl(context, entry.target) }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MALL_CATEGORIES.forEach { entry ->
                MallEntryCell(entry, BiliAccent) {
                    query = entry.target
                    keyword = entry.target
                    channel = -1
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF2A1220), Color(0xFF3A1A2A)))
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    if (searching) "搜索结果：$keyword" else "哔哩哔哩会员购",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (searching) "来自会员购推荐流的匹配商品" else "正品手办 · 周边 · 演出票务 · 官方直发",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && products.isEmpty() -> CircularProgressIndicator(
                    color = BiliAccent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.align(Alignment.Center).size(26.dp)
                )

                failed && products.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("商城加载失败", color = appTextMuted(), fontSize = 13.sp)
                    Text(
                        text = "点击重试",
                        color = BiliAccent,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .noRippleClickable { reloadKey += 1 }
                    )
                }

                products.isEmpty() && searching -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("未找到「$keyword」相关商品", color = appTextMuted(), fontSize = 13.sp)
                    Text(
                        text = "去会员购网页搜索",
                        color = BiliAccent,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .noRippleClickable { openUrl(context, mallSearchUrl(keyword)) }
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(products, key = { it.id.ifBlank { it.title } }) { item ->
                        MallCard(item, modifier = Modifier.animateItem()) { detail = item }
                    }
                    if (loading) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = BiliAccent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    if (endReached && !loading && !searching) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("没有更多商品了", color = appTextMuted(), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    val current = detail
    if (current != null) {
        MallDetailSheet(
            item = current,
            onDismiss = { detail = null },
            onOpenWeb = {
                openUrl(context, current.url.ifBlank { mallSearchUrl(current.title) })
            }
        )
    }
}

@Composable
private fun MallEntryCell(entry: MallEntry, tint: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(62.dp)
            .noRippleClickable { onClick() }
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = entry.label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(entry.label, color = appTextMuted(), fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun MallCard(item: MallItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(appSurface())
            .pressClickable(pressedScale = 0.96f) { onClick() }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio((1f / item.ratio).coerceIn(0.6f, 1.5f))
        ) {
            RemoteImage(
                url = item.image,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                targetWidth = 540,
                placeholder = Color(0xFF1B1B1F)
            )
            if (item.tag.isNotBlank()) {
                Text(
                    text = item.tag,
                    color = appText(),
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            text = item.title,
            color = appText(),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Row(
            Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.priceSymbol,
                color = BiliAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = item.priceText.ifBlank { "查看" },
                color = BiliAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MallDetailSheet(
    item: MallItem,
    onDismiss: () -> Unit,
    onOpenWeb: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appSurface(),
        contentColor = appText()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 18.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .width(120.dp)
                        .aspectRatio((1f / item.ratio).coerceIn(0.7f, 1.3f))
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    RemoteImage(
                        url = item.image,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        targetWidth = 480,
                        placeholder = Color(0xFF1B1B1F)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = appText(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = item.priceSymbol,
                            color = BiliAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = item.priceText.ifBlank { "见详情" },
                            color = BiliAccent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (item.tag.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = item.tag,
                            color = appTextMuted(),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(BiliAccent)
                    .pressClickable(pressedScale = 0.97f) { onOpenWeb() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("去会员购购买", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(appField())
                    .pressClickable(pressedScale = 0.97f) {
                        val clip = android.content.ClipboardManager::class.java
                        val manager = context.getSystemService(clip) as android.content.ClipboardManager
                        manager.setPrimaryClip(
                            android.content.ClipData.newPlainText("mall", item.url)
                        )
                        android.widget.Toast.makeText(context, "链接已复制", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("复制商品链接", color = appText(), fontSize = 14.sp)
            }
        }
    }
}


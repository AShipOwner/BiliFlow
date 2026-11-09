package com.biliflow.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliflow.app.data.AuthManager
import com.biliflow.app.data.BiliApi
import com.biliflow.app.data.AuthState
import com.biliflow.app.data.FeedSource
import com.biliflow.app.data.LoginPrompt
import com.biliflow.app.data.VideoItem

@Composable
fun FriendsScreen(
    viewModel: FeedViewModel,
    onSearch: () -> Unit,
    onOpenSpace: (Long) -> Unit = {}
) {
    val authState by AuthManager.state.collectAsStateCompat()
    val loggedIn = authState is AuthState.LoggedIn
    val state by viewModel.state(FeedSource.FOLLOW).collectAsStateCompat()
    val context = LocalContext.current
    var commentItem by remember { mutableStateOf<VideoItem?>(null) }
    var coinItem by remember { mutableStateOf<VideoItem?>(null) }
    var mutualMids by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var mutualLoaded by remember { mutableStateOf(false) }
    var mutualNames by remember { mutableStateOf<List<com.biliflow.app.data.FollowUser>>(emptyList()) }

    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            viewModel.ensureLoaded(FeedSource.FOLLOW)
            val list = runCatching {
                val first = BiliApi.followings(AuthManager.mid, 1)
                val second = runCatching { BiliApi.followings(AuthManager.mid, 2) }.getOrDefault(emptyList())
                first + second
            }.getOrDefault(emptyList())
            mutualNames = list.filter { it.mutual }
            mutualMids = mutualNames.map { it.mid }.toSet()
            mutualLoaded = true
        }
    }

    val mutualItems = if (mutualLoaded && mutualMids.isNotEmpty()) {
        state.items.filter { it.ownerMid in mutualMids }
    } else {
        emptyList()
    }
    val visibleState = if (mutualItems.isNotEmpty()) state.copy(items = mutualItems) else state

    Box(Modifier.fillMaxSize().background(appBackground())) {
        if (!loggedIn) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "登录后查看朋友动态",
                        color = appText(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "互相关注与关注用户的视频更新",
                        color = appTextMuted(),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { LoginPrompt.show() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BiliAccent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("扫码登录")
                    }
                }
            }
        } else {
            FeedPager(
                source = FeedSource.FOLLOW,
                state = visibleState,
                viewModel = viewModel,
                onComment = { commentItem = it },
                onCoin = { coinItem = it },
                onOpenOwner = { onOpenSpace(it.ownerMid) }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 14.dp)
        ) {
            Text(
                text = "朋友",
                color = if (loggedIn) Color.White else appText(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(40.dp)
                    .pressClickable(pressedScale = 0.85f) { onSearch() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索",
                    tint = if (loggedIn) Color.White else appText(),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    val sheetItem = commentItem
    if (sheetItem != null) {
        CommentsSheet(
            item = sheetItem,
            viewModel = viewModel,
            onDismiss = { commentItem = null },
            onOpenSpace = { mid ->
                commentItem = null
                onOpenSpace(mid)
            }
        )
    }
    val coin = coinItem
    if (coin != null) {
        CoinSheet(
            item = coin,
            user = (authState as? AuthState.LoggedIn)?.user,
            social = viewModel.socialOf(coin.bvid),
            onDismiss = { coinItem = null },
            onConfirm = { count, alsoLike ->
                viewModel.addCoin(coin, count, alsoLike)
                coinItem = null
            }
        )
    }
}

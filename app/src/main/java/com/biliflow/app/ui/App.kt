package com.biliflow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biliflow.app.data.AuthManager
import com.biliflow.app.data.AuthState
import com.biliflow.app.data.LiveRoom
import com.biliflow.app.data.LoginPrompt
import com.biliflow.app.data.VideoItem

@Composable
fun BiliFlowApp() {
    val viewModel: FeedViewModel = viewModel()
    val authState by AuthManager.state.collectAsStateCompat()
    val loggedIn = authState is AuthState.LoggedIn
    var tab by rememberSaveable { mutableStateOf(0) }
    var playerItem by remember { mutableStateOf<VideoItem?>(null) }
    var liveRoom by remember { mutableStateOf<LiveRoom?>(null) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showPublish by remember { mutableStateOf(false) }
    var spaceMid by remember { mutableStateOf<Long?>(null) }
    var webUrl by remember { mutableStateOf<String?>(null) }
    var userListMode by remember { mutableStateOf<Int?>(null) }

    BackHandler {
        when {
            webUrl != null -> webUrl = null
            userListMode != null -> userListMode = null
            spaceMid != null -> spaceMid = null
            playerItem != null -> playerItem = null
            liveRoom != null -> liveRoom = null
            showSearch -> showSearch = false
            showPublish -> showPublish = false
            tab != 0 -> tab = 0
        }
    }

    Box(Modifier.fillMaxSize().background(appBackground())) {
        UiHosts()
        val current = playerItem
        val room = liveRoom
        when {
            current != null -> SoloPlayerScreen(
                seed = current,
                viewModel = viewModel,
                onOpenSpace = { mid -> android.util.Log.d("BiliFlow", "open space $mid"); spaceMid = mid },
                onBack = { playerItem = null }
            )

            room != null -> LivePlayerScreen(room = room, onBack = { liveRoom = null })

            showSearch -> SearchScreen(
                onOpenVideo = { playerItem = it },
                onBack = { showSearch = false }
            )

            else -> {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        fadeIn(tween(190)) togetherWith fadeOut(tween(110))
                    },
                    label = "mainTab"
                ) { currentTab ->
                    when (currentTab) {
                        0 -> HomeScreen(
                            viewModel = viewModel,
                            onSearch = { showSearch = true },
                            onOpenLive = { liveRoom = it },
                            onOpenSpace = { mid -> android.util.Log.d("BiliFlow", "open space $mid"); spaceMid = mid }
                        )

                        1 -> FriendsScreen(
                            viewModel = viewModel,
                            onSearch = { showSearch = true },
                            onOpenSpace = { spaceMid = it }
                        )

                        3 -> MessagesScreen(
                            onOpenVideo = { playerItem = it },
                            onOpenSpace = { spaceMid = it },
                            onOpenWeb = { webUrl = it }
                        )

                        else -> ProfileScreen(
                            onOpenVideo = { playerItem = it },
                            onOpenUserList = { userListMode = it },
                            onOpenWeb = { webUrl = it }
                        )
                    }
                }
                DouyinBottomNav(
                    selected = tab,
                    onSelect = { index ->
                        if (index == 4 && !loggedIn) {
                            LoginPrompt.show()
                        } else {
                            tab = index
                        }
                    },
                    onPublish = { showPublish = true },
                    immersive = tab == 0 || tab == 1,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        if (showPublish) {
            PublishSheet(onDismiss = { showPublish = false })
        }

        val ul = userListMode
        if (ul != null) {
            UserListScreen(
                mode = ul,
                onOpenSpace = { spaceMid = it },
                onBack = { userListMode = null }
            )
        }

        val web = webUrl
        if (web != null) {
            InAppWebScreen(url = web, onClose = { webUrl = null })
        }

        val space = spaceMid
        if (space != null && space > 0L) {
            SpaceScreen(
                mid = space,
                onOpenVideo = {
                    spaceMid = null
                    playerItem = it
                },
                onBack = { spaceMid = null }
            )
        }
    }
}

@Composable
private fun DouyinBottomNav(
    selected: Int,
    onSelect: (Int) -> Unit,
    onPublish: () -> Unit,
    immersive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val background by animateColorAsState(
        targetValue = if (immersive) Color.Black else appSurface(),
        animationSpec = tween(220),
        label = "navBackground"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .navigationBarsPadding()
            .height(52.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem(0, selected == 0, Icons.Filled.Home, "首页", onSelect, Modifier.weight(1f), immersive)
        NavItem(1, selected == 1, Icons.Filled.People, "朋友", onSelect, Modifier.weight(1f), immersive)
        Box(
            modifier = Modifier
                .weight(1f)
                .pressClickable(pressedScale = 0.86f) { onPublish() },
            contentAlignment = Alignment.Center
        ) {
            PublishButton()
        }
        NavItem(3, selected == 3, Icons.Filled.ChatBubble, "消息", onSelect, Modifier.weight(1f), immersive)
        NavItem(4, selected == 4, Icons.Filled.Person, "我", onSelect, Modifier.weight(1f), immersive)
    }
}

@Composable
private fun PublishButton() {
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(30.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .matchParentSize()
                .padding(end = 5.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(BiliCyan)
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(start = 5.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(BiliAccent)
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(horizontal = 3.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "发布",
                tint = Color.Black,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun NavItem(
    index: Int,
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    immersive: Boolean = false
) {
    val active = if (immersive) Color.White else appText()
    val inactive = if (immersive) Color.White.copy(alpha = 0.6f) else appTextMuted()
    val tint by animateColorAsState(
        targetValue = if (selected) active else inactive,
        animationSpec = tween(200),
        label = "navTint"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = BiliMotion.popSpring(),
        label = "navScale"
    )
    Column(
        modifier = modifier.pressClickable(pressedScale = 0.9f) { onSelect(index) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}


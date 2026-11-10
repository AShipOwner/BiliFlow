package com.biliflow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
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
    var homeTab by rememberSaveable { mutableStateOf(HomeTab.RECOMMEND) }
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
                        val forward = targetState > initialState
                        val direction = if (forward) 1 else -1
                        (fadeIn(tween(200)) + slideInHorizontally(
                            tween(240, easing = FastOutSlowInEasing)
                        ) { it / 14 * direction }) togetherWith
                            (fadeOut(tween(120)) + slideOutHorizontally(
                                tween(160, easing = FastOutSlowInEasing)
                            ) { -it / 18 * direction })
                    },
                    label = "mainTab"
                ) { currentTab ->
                    when (currentTab) {
                        0 -> HomeScreen(
                            viewModel = viewModel,
                            homeTab = homeTab,
                            onHomeTabChange = { homeTab = it },
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
                BiliFlowBottomNav(
                    selected = tab,
                    onSelect = { index ->
                        if (index == 4 && !loggedIn) {
                            LoginPrompt.show()
                        } else {
                            tab = index
                        }
                    },
                    onPublish = { if (loggedIn) showPublish = true else LoginPrompt.show() },
                    immersive = tab == 1 || (tab == 0 && (homeTab == HomeTab.FOLLOW || homeTab == HomeTab.RECOMMEND)),
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

        UiHosts()
    }
}

@Composable
private fun BiliFlowBottomNav(
    selected: Int,
    onSelect: (Int) -> Unit,
    onPublish: () -> Unit,
    immersive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val background by animateColorAsState(
        targetValue = if (immersive) Color.Black.copy(alpha = 0.94f) else appSurface(),
        animationSpec = tween(220),
        label = "navBackground"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = if (immersive) Color.White.copy(alpha = 0.08f) else appDivider()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(0, selected == 0, Icons.Filled.Home, Icons.Outlined.Home, "首页", onSelect, Modifier.weight(1f), immersive)
            NavItem(1, selected == 1, Icons.Filled.People, Icons.Outlined.People, "朋友", onSelect, Modifier.weight(1f), immersive)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pressClickable(pressedScale = 0.92f) { onPublish() },
                contentAlignment = Alignment.Center
            ) {
                PublishButton()
            }
            NavItem(3, selected == 3, Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "消息", onSelect, Modifier.weight(1f), immersive)
            NavItem(4, selected == 4, Icons.Filled.Person, Icons.Outlined.Person, "我", onSelect, Modifier.weight(1f), immersive)
        }
    }
}

@Composable
private fun PublishButton() {
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BiliAccent),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "发布视频",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun NavItem(
    index: Int,
    selected: Boolean,
    selectedIcon: ImageVector,
    icon: ImageVector,
    label: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    immersive: Boolean = false
) {
    val active = if (immersive) Color.White else appText()
    val inactive = if (immersive) Color.White.copy(alpha = 0.52f) else appTextMuted()
    val tint by animateColorAsState(
        targetValue = if (selected) active else inactive,
        animationSpec = tween(180),
        label = "navTint"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = BiliMotion.popSpring(),
        label = "navScale"
    )
    Column(
        modifier = modifier.pressClickable(pressedScale = 0.96f) { onSelect(index) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected && !immersive) appAccentSoft() else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}


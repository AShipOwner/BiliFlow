package com.biliflow.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliflow.app.data.AuthManager
import com.biliflow.app.data.AuthState
import com.biliflow.app.data.BiliApi
import com.biliflow.app.data.DynamicItem
import com.biliflow.app.data.FavFolder
import com.biliflow.app.data.LocalStore
import com.biliflow.app.data.NetworkMonitor
import com.biliflow.app.data.QualityMode
import com.biliflow.app.data.UiMessages
import com.biliflow.app.data.UserHomeCard
import com.biliflow.app.data.VideoItem
import com.biliflow.app.data.effectiveQualityQn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ProfileScreen(
    onOpenVideo: (VideoItem) -> Unit,
    onOpenUserList: (Int) -> Unit = {},
    onOpenWeb: (String) -> Unit = {}
) {
    val authState by AuthManager.state.collectAsStateCompat()
    val user = (authState as? AuthState.LoggedIn)?.user
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localName by LocalStore.localName.collectAsStateCompat()
    val localSign by LocalStore.localSign.collectAsStateCompat()
    val localAvatar by LocalStore.localAvatar.collectAsStateCompat()
    val localBanner by LocalStore.localBanner.collectAsStateCompat()

    var card by remember { mutableStateOf<UserHomeCard?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    var works by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var workPage by remember { mutableIntStateOf(1) }
    var worksEnd by remember { mutableStateOf(false) }
    var dynamics by remember { mutableStateOf<List<DynamicItem>>(emptyList()) }
    var dynOffset by remember { mutableStateOf("") }
    var folders by remember { mutableStateOf<List<FavFolder>>(emptyList()) }
    var favVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(user?.mid, localName, localSign) {
        val mid = user?.mid ?: 0L
        if (mid <= 0L) return@LaunchedEffect
        loading = true
        card = runCatching { BiliApi.userHomeCard(mid) }.getOrNull()
        works = runCatching { BiliApi.spaceVideos(mid, 1) }.getOrDefault(emptyList())
        loading = false
    }

    LaunchedEffect(tab, user?.mid) {
        val mid = user?.mid ?: 0L
        if (mid <= 0L) return@LaunchedEffect
        when (tab) {
            1 -> if (dynamics.isEmpty()) {
                dynamics = runCatching { BiliApi.userDynamics(mid, "") }.getOrDefault(emptyList())
            }
            2 -> if (folders.isEmpty()) {
                folders = runCatching { BiliApi.favoriteFolders() }.getOrDefault(emptyList())
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackground())
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 76.dp)
    ) {
        item {
            ProfileHeader(
                name = localName.ifBlank { card?.name.orEmpty().ifBlank { user?.name.orEmpty().ifBlank { "未登录" } } },
                uid = user?.mid ?: 0L,
                avatar = localAvatar.ifBlank { card?.face.orEmpty().ifBlank { user?.face.orEmpty() } },
                banner = localBanner.ifBlank { card?.banner.orEmpty() },
                onMenu = { showSettings = true }
            )
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset(y = (-20).dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(appSurface())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatColumn("获赞", card?.likes ?: 0L)
                    StatColumn("关注", (card?.following ?: 0).toLong(), clickable = true) { onOpenUserList(1) }
                    StatColumn("粉丝", (card?.follower ?: 0).toLong(), clickable = true) { onOpenUserList(2) }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "编辑主页",
                        color = appText(),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(appField())
                            .pressClickable(pressedScale = 0.95f) {
                                if ((user?.mid ?: 0L) > 0L) showEdit = true else UiMessages.show("请先登录")
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = localSign.ifBlank {
                        card?.sign.orEmpty().ifBlank { "点击添加介绍，让大家认识你…" }
                    },
                    color = if (localSign.isBlank() && card?.sign.isNullOrBlank()) appTextMuted() else appText(),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable { showEdit = true }
                        .padding(horizontal = 18.dp)
                )

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(appField())
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("作品", "日常", "收藏").forEachIndexed { index, label ->
                        val selected = tab == index
                        val tabBackground by animateColorAsState(
                            targetValue = if (selected) appSurface() else Color.Transparent,
                            animationSpec = tween(180),
                            label = "profileTabBackground"
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(tabBackground)
                                .pressClickable(pressedScale = 0.97f) { tab = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (index == 0) "$label ${works.size}" else label,
                                color = if (selected) appText() else appTextMuted(),
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BiliAccent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
        }

        when (tab) {
            0 -> {
                if (!loading && works.isEmpty()) item { EmptyBlock("还没有作品") }
                items(works.chunked(3)) { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { video ->
                            WorkCell(video, Modifier.weight(1f)) { onOpenVideo(video) }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (!worksEnd && works.isNotEmpty()) {
                    item {
                        Text(
                            text = "加载更多",
                            color = appTextMuted(),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable {
                                    val mid = user?.mid ?: return@noRippleClickable
                                    scope.launch {
                                        workPage += 1
                                        val next = runCatching { BiliApi.spaceVideos(mid, workPage) }
                                            .getOrDefault(emptyList())
                                        if (next.isEmpty()) worksEnd = true else works = works + next
                                    }
                                }
                                .padding(vertical = 14.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            1 -> {
                if (dynamics.isEmpty()) item { EmptyBlock("还没有动态") }
                items(dynamics) { dynamic ->
                    DynamicRow(dynamic) { bv ->
                        if (bv.isNotBlank()) onOpenVideo(VideoItem(bvid = bv, title = dynamic.title))
                    }
                    HorizontalDivider(color = appDivider(), modifier = Modifier.padding(horizontal = 16.dp))
                }
                if (dynamics.isNotEmpty()) {
                    item {
                        Text(
                            text = "加载更多",
                            color = appTextMuted(),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable {
                                    val mid = user?.mid ?: return@noRippleClickable
                                    scope.launch {
                                        val next = runCatching { BiliApi.userDynamics(mid, dynOffset) }
                                            .getOrDefault(emptyList())
                                        if (next.isNotEmpty()) {
                                            dynamics = dynamics + next
                                            dynOffset = next.lastOrNull()?.id.orEmpty()
                                        } else {
                                            UiMessages.show("没有更多动态了")
                                        }
                                    }
                                }
                                .padding(vertical = 14.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            else -> {
                if (favVideos.isEmpty()) {
                    items(folders) { folder ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .noRippleClickable {
                                    scope.launch {
                                        favVideos = runCatching { BiliApi.favoriteResources(folder.id, 1) }
                                            .getOrDefault(emptyList())
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(folder.title, color = appText(), fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text("${folder.count}", color = appTextMuted(), fontSize = 12.sp)
                        }
                        HorizontalDivider(color = appDivider(), modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    if (folders.isEmpty()) item { EmptyBlock("还没有收藏夹") }
                } else {
                    items(favVideos.chunked(3)) { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            row.forEach { video ->
                                WorkCell(video, Modifier.weight(1f)) { onOpenVideo(video) }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    item {
                        Text(
                            text = "返回收藏夹",
                            color = appTextMuted(),
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable { favVideos = emptyList() }
                                .padding(vertical = 14.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        ProfileSettingsSheet(
            onDismiss = { showSettings = false },
            onOpenWeb = onOpenWeb,
            mid = user?.mid ?: 0L
        )
    }

    if (showEdit) {
        ProfileEditSheet(
            currentName = localName.ifBlank { card?.name.orEmpty() },
            currentSign = localSign.ifBlank { card?.sign.orEmpty() },
            currentAvatar = localAvatar.ifBlank { card?.face.orEmpty() },
            currentBanner = localBanner.ifBlank { card?.banner.orEmpty() },
            onDismiss = { showEdit = false },
            onSave = { n, s, a, b ->
                LocalStore.setLocalProfile(n, s, a, b)
                showEdit = false
                UiMessages.show("主页资料已更新")
            }
        )
    }
}

@Composable
private fun ProfileHeader(
    name: String,
    uid: Long,
    avatar: String,
    banner: String,
    onMenu: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(appField())
    ) {
        if (banner.isNotBlank()) {
            RemoteImage(
                url = banner,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                targetWidth = 1080,
                placeholder = appField()
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f)))
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(BiliAccent.copy(alpha = 0.20f), appBackground())
                        )
                    )
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.30f))
                    .pressClickable(pressedScale = 0.94f) { onMenu() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Settings, "设置", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(appSurface())
                    .padding(3.dp)
                    .clip(CircleShape)
            ) {
                RemoteImage(
                    url = avatar,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    targetWidth = 280
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = name,
                    color = if (banner.isNotBlank()) Color.White else appText(),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (uid > 0L) "UID $uid" else "登录后完善你的主页",
                    color = if (banner.isNotBlank()) Color.White.copy(alpha = 0.78f) else appTextMuted(),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: Long, clickable: Boolean = false, onClick: () -> Unit = {}) {
    val animatedValue by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "statCount"
    )
    Column(
        Modifier
            .padding(end = 22.dp)
            .pressClickable(enabled = clickable, pressedScale = 0.92f) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (value > 0) formatCount(animatedValue.toLong()) else "0",
            color = appText(),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = DouyinNumber
        )
        Spacer(Modifier.height(3.dp))
        Text(label, color = appTextMuted(), fontSize = 11.sp)
    }
}

@Composable
private fun WorkCell(video: VideoItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.pressClickable(pressedScale = 0.97f) { onClick() }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(14.dp))
                .background(appField())
        ) {
            RemoteImage(
                url = video.cover,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                targetWidth = 300,
                placeholder = appField()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.62f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.58f)
                        )
                    )
            )
            Row(
                Modifier.align(Alignment.BottomStart).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(2.dp))
                Text(formatCount(video.view), color = Color.White, fontSize = 10.sp)
            }
        }
        Text(
            text = video.title,
            color = appText(),
            fontSize = 12.sp,
            maxLines = 2,
            lineHeight = 17.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
    }
}

@Composable
private fun DynamicRow(dynamic: DynamicItem, onOpenVideo: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(appChip())
            ) {
                RemoteImage(
                    url = dynamic.authorFace,
                    contentDescription = dynamic.authorName,
                    modifier = Modifier.fillMaxSize(),
                    targetWidth = 120
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = dynamic.authorName.ifBlank { "我" },
                    color = appText(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (dynamic.pubTs > 0) formatTimeAgo(dynamic.pubTs) else "",
                    color = appTextMuted(),
                    fontSize = 10.sp
                )
            }
        }
        if (dynamic.text.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(dynamic.text, color = appText(), fontSize = 13.sp, lineHeight = 19.sp)
        }
        if (dynamic.image.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.68f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                RemoteImage(
                    url = dynamic.image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        if (dynamic.bvid.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(appField())
                    .noRippleClickable { onOpenVideo(dynamic.bvid) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(appField())
                ) {
                    RemoteImage(
                        url = dynamic.image,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        targetWidth = 160
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = dynamic.title.ifBlank { dynamic.text },
                    color = appText(),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyBlock(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
        AppEmptyState(title = text, description = "内容更新后会显示在这里")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditSheet(
    currentName: String,
    currentSign: String,
    currentAvatar: String,
    currentBanner: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(currentName) }
    var sign by remember { mutableStateOf(currentSign) }
    var avatar by remember { mutableStateOf(currentAvatar) }
    var banner by remember { mutableStateOf(currentBanner) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pickTarget by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = pickTarget
        pickTarget = ""
        if (uri != null && target.isNotBlank()) {
            val fileName = if (target == "avatar") "profile_avatar.jpg" else "profile_banner.jpg"
            val path = runCatching {
                context.contentResolver.openInputStream(uri).use { input ->
                    val out = File(context.filesDir, fileName)
                    out.outputStream().use { input?.copyTo(it) }
                    out.absolutePath
                }
            }.getOrNull()
            if (path != null) {
                if (target == "avatar") avatar = path else banner = path
                UiMessages.show(if (target == "avatar") "头像已上传，保存后生效" else "背景已上传，保存后生效")
            } else {
                UiMessages.show("读取图片失败")
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appSurface(),
        contentColor = appText(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 22.dp)) {
            Text("编辑主页", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = appText())
            Spacer(Modifier.height(4.dp))
            Text(
                "昵称/签名保存时同步到 B 站；头像/背景可从相册上传，本机生效",
                fontSize = 11.sp,
                color = appTextMuted()
            )
            Spacer(Modifier.height(14.dp))
            EditField("昵称", name) { name = it.take(20) }
            EditField("签名", sign) { sign = it.take(80) }
            UploadRow("头像", avatar, isCircle = true) {
                pickTarget = "avatar"
                picker.launch("image/*")
            }
            UploadRow("背景", banner, isCircle = false) {
                pickTarget = "banner"
                picker.launch("image/*")
            }
            Spacer(Modifier.height(14.dp))
            AppPrimaryButton(
                text = "保存并同步到 B 站",
                onClick = {
                    onSave(name.trim(), sign.trim(), avatar.trim(), banner.trim())
                    scope.launch {
                        runCatching { BiliApi.updateProfile(name.trim(), sign.trim()) }
                            .onSuccess { UiMessages.show("已同步昵称/签名到 B 站") }
                            .onFailure { UiMessages.show("云端同步失败：${it.message}") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun UploadRow(label: String, value: String, isCircle: Boolean, onPick: () -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, color = appTextMuted(), fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(if (isCircle) 52.dp else 72.dp)
                    .clip(if (isCircle) CircleShape else RoundedCornerShape(8.dp))
                    .background(appField())
            ) {
                if (value.isNotBlank()) {
                    RemoteImage(
                        url = value,
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        targetWidth = 200
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "从相册上传",
                color = BiliAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(appAccentSoft())
                    .pressClickable(pressedScale = 0.96f) { onPick() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun EditField(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(vertical = 7.dp)) {
        Text(label, color = appTextMuted(), fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(appField())
                .padding(horizontal = 14.dp),
            textStyle = androidx.compose.ui.text.TextStyle(color = appText(), fontSize = 14.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(BiliAccent)
        )
    }
}

@Composable
private fun SettingChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (selected) appAccentSoft() else appChip(),
        animationSpec = tween(180),
        label = "settingChipBackground"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) BiliAccent else appTextMuted(),
        animationSpec = tween(180),
        label = "settingChipText"
    )
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .pressClickable(pressedScale = 0.96f) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSettingsSheet(
    onDismiss: () -> Unit,
    onOpenWeb: (String) -> Unit,
    mid: Long
) {
    val qualityMode by LocalStore.qualityMode.collectAsStateCompat()
    val danmakuEnabled by LocalStore.danmakuEnabled.collectAsStateCompat()
    val themeMode by LocalStore.themeMode.collectAsStateCompat()
    val network by NetworkMonitor.profile.collectAsStateCompat()
    val authState by AuthManager.state.collectAsStateCompat()
    val isVip = (authState as? AuthState.LoggedIn)?.user?.isVip == true
    val targetQn = effectiveQualityQn(qualityMode, isVip, network)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmLogout by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appSurface(),
        contentColor = appText(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 22.dp)) {
            Text("设置", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = appText())
            Spacer(Modifier.height(12.dp))

            Text("外观", fontSize = 13.sp, color = appText())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("跟随系统" to 0, "浅色" to 1, "深色" to 2).forEach { (label, mode) ->
                    SettingChip(label, themeMode == mode) { LocalStore.setThemeMode(mode) }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("清晰度（当前 ${qualityLabel(targetQn)}）", fontSize = 13.sp, color = appText())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QualityMode.entries.forEach { mode ->
                    SettingChip(mode.label, qualityMode == mode) { LocalStore.setQualityMode(mode) }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("弹幕", fontSize = 14.sp, color = appText(), modifier = Modifier.weight(1f))
                Switch(
                    checked = danmakuEnabled,
                    onCheckedChange = { LocalStore.setDanmakuEnabled(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = BiliAccent)
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "清除图片缓存",
                fontSize = 14.sp,
                color = appText(),
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable {
                        com.biliflow.app.data.ImageLoader.clear()
                        UiMessages.show("图片缓存已清理")
                    }
                    .padding(vertical = 10.dp)
            )

            if (mid > 0L) {
                Text(
                    text = "网页版主页",
                    fontSize = 14.sp,
                    color = appText(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable { onOpenWeb("https://space.bilibili.com/$mid") }
                        .padding(vertical = 10.dp)
                )
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = appDivider())
            Spacer(Modifier.height(6.dp))

            if (!confirmLogout) {
                Text(
                    text = "退出登录",
                    fontSize = 14.sp,
                    color = BiliAccent,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable { confirmLogout = true }
                        .padding(vertical = 12.dp)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("确认退出当前账号？", fontSize = 13.sp, color = appText(), modifier = Modifier.weight(1f))
                    TextButton(onClick = { confirmLogout = false }) { Text("取消", color = appTextMuted()) }
                    TextButton(onClick = {
                        AuthManager.logout()
                        UiMessages.show("已退出登录")
                        onDismiss()
                    }) { Text("退出", color = BiliAccent) }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "BiliFlow 0.9.5 · 数据来自哔哩哔哩公开接口",
                fontSize = 11.sp,
                color = appTextMuted()
            )
        }
    }
}

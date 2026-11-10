package com.biliflow.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliflow.app.data.AuthManager
import com.biliflow.app.data.AuthState
import com.biliflow.app.data.BiliApi
import com.biliflow.app.data.CommentItem
import com.biliflow.app.data.CommentPicture
import com.biliflow.app.data.LoginPrompt
import com.biliflow.app.data.UiMessages
import com.biliflow.app.data.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    item: VideoItem,
    viewModel: FeedViewModel,
    onDismiss: () -> Unit,
    onOpenSpace: (Long) -> Unit = {}
) {
    val authState by AuthManager.state.collectAsStateCompat()
    val account = (authState as? AuthState.LoggedIn)?.user
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    var sort by remember { mutableIntStateOf(2) }
    var aid by remember { mutableLongStateOf(item.aid) }
    var ownerMid by remember { mutableLongStateOf(item.ownerMid) }
    var comments by remember { mutableStateOf<List<CommentItem>>(emptyList()) }
    var topComments by remember { mutableStateOf<List<CommentItem>>(emptyList()) }
    var total by remember { mutableLongStateOf(item.reply) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }

    var input by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<CommentItem?>(null) }
    var sending by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CommentItem?>(null) }
    var replyPages by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var replyLoads by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val accountMid = account?.mid ?: 0L
    val loggedIn = account != null

    LaunchedEffect(loggedIn) {
        if (!loggedIn) replyTarget = null
    }

    LaunchedEffect(item.bvid, sort, reloadToken) {
        loading = true
        error = null
        page = 1
        comments = emptyList()
        topComments = emptyList()
        replyPages = emptyMap()
        try {
            val target = if (aid > 0L) item else viewModel.detail(item)
            if (target != null) {
                aid = target.aid
                ownerMid = target.ownerMid
            }
            if (aid <= 0L) {
                error = "无法获取视频信息"
                return@LaunchedEffect
            }
            val result = BiliApi.commentPage(aid, 1, sort)
            comments = result.items
            topComments = result.topItems
            total = if (result.total > 0L) result.total else total
            hasMore = result.hasMore
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            error = exception.message ?: "评论加载失败"
        } finally {
            loading = false
        }
    }

    fun loadMore() {
        if (loading || loadingMore || !hasMore || aid <= 0L) return
        loadingMore = true
        scope.launch {
            try {
                val next = page + 1
                val result = BiliApi.commentPage(aid, next, sort)
                comments = mergeComments(comments, result.items)
                page = next
                hasMore = result.hasMore
                if (result.total > 0L) total = result.total
            } catch (exception: Exception) {
                UiMessages.show(exception.message ?: "加载更多评论失败")
            } finally {
                loadingMore = false
            }
        }
    }

    fun loadReplies(root: CommentItem, refresh: Boolean = false) {
        if (aid <= 0L || replyLoads.contains(root.rpid)) return
        val nextPage = when {
            refresh -> 1
            replyPages[root.rpid] == null -> 1
            else -> (replyPages[root.rpid] ?: 1) + 1
        }
        replyLoads = replyLoads + root.rpid
        scope.launch {
            try {
                val result = BiliApi.commentReplies(aid, root.rpid, nextPage)
                comments = updateCommentList(comments, root.rpid) { current ->
                    val merged = if (nextPage == 1) {
                        result.items
                    } else {
                        mergeComments(current.replies, result.items)
                    }
                    current.copy(
                        replies = merged,
                        replyCount = if (result.total > 0L) result.total else current.replyCount
                    )
                }
                topComments = updateCommentList(topComments, root.rpid) { current ->
                    val merged = if (nextPage == 1) {
                        result.items
                    } else {
                        mergeComments(current.replies, result.items)
                    }
                    current.copy(
                        replies = merged,
                        replyCount = if (result.total > 0L) result.total else current.replyCount
                    )
                }
                replyPages = replyPages + (root.rpid to nextPage)
            } catch (exception: Exception) {
                UiMessages.show(exception.message ?: "回复加载失败")
            } finally {
                replyLoads = replyLoads - root.rpid
            }
        }
    }

    fun toggleCommentLike(target: CommentItem) {
        if (!AuthManager.ensureLogin()) return
        if (aid <= 0L) return
        val like = !target.liked
        val delta = if (like) 1L else -1L
        val previousComments = comments
        val previousTop = topComments
        val transform: (CommentItem) -> CommentItem = {
            it.copy(liked = like, like = (it.like + delta).coerceAtLeast(0L))
        }
        comments = updateCommentList(comments, target.rpid, transform)
        topComments = updateCommentList(topComments, target.rpid, transform)
        scope.launch {
            try {
                BiliApi.likeComment(aid, target.rpid, like)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                comments = previousComments
                topComments = previousTop
                handleCommentError(exception, if (like) "点赞评论失败" else "取消点赞失败")
            }
        }
    }

    fun sendComment() {
        val text = input.trim()
        if (text.isBlank() || sending) return
        if (!AuthManager.ensureLogin()) return
        if (aid <= 0L) return
        val target = replyTarget
        sending = true
        scope.launch {
            try {
                val newRpid = BiliApi.postComment(
                    aid = aid,
                    message = text,
                    root = target?.let { if (it.root > 0L) it.root else it.rpid } ?: 0L,
                    parent = target?.rpid ?: 0L
                )
                input = ""
                replyTarget = null
                focusManager.clearFocus()
                keyboard?.hide()
                UiMessages.show("评论已发布")
                reloadToken++
                if (newRpid > 0L) {
                    scope.launch {
                        when (BiliApi.checkCommentVisibility(aid, newRpid)) {
                            BiliApi.CommentVisibility.VISIBLE ->
                                UiMessages.show("反诈自检：评论对他人可见")
                            BiliApi.CommentVisibility.SELF_ONLY ->
                                UiMessages.show("反诈自检：仅自己可见，疑似被折叠")
                            BiliApi.CommentVisibility.MISSING ->
                                UiMessages.show("反诈自检：未查到该评论，可能审核中或被删")
                            else -> Unit
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                handleCommentError(exception, "评论发布失败")
            } finally {
                sending = false
            }
        }
    }

    fun deleteComment(target: CommentItem) {
        if (aid <= 0L) return
        scope.launch {
            try {
                BiliApi.deleteComment(aid, target.rpid)
                comments = removeComment(comments, target.rpid)
                topComments = removeComment(topComments, target.rpid)
                total = (total - 1L).coerceAtLeast(0L)
                UiMessages.show("评论已删除")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                handleCommentError(exception, "删除评论失败")
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appSurface(),
        contentColor = appText(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = appDivider()) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (total > 0L) "评论 ${formatCount(total)}" else "评论",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                SortChip("热门", sort == 2) { sort = 2 }
                Spacer(Modifier.width(8.dp))
                SortChip("最新", sort == 0) { sort = 0 }
            }
            HorizontalDivider(color = appDivider())

            when {
                loading -> {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        CommentsSkeleton()
                    }
                }

                error != null -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AppErrorState(message = error.orEmpty(), onRetry = { reloadToken++ })
                    }
                }

                comments.isEmpty() && topComments.isEmpty() -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AppEmptyState(
                            title = if (loggedIn) "还没有评论" else "登录后查看并参与评论",
                            description = if (loggedIn) "说点什么，成为第一个评论的人" else "登录后可以点赞、回复和发送表情",
                            actionLabel = if (loggedIn) null else "去登录",
                            onAction = { LoginPrompt.show() }
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        if (topComments.isNotEmpty()) {
                            items(topComments, key = { "top-${it.rpid}" }) { comment ->
                                CommentThread(
                                    comment = comment,
                                    isTop = true,
                                    ownerMid = ownerMid,
                                    accountMid = accountMid,
                                    loggedIn = loggedIn,
                                    replyLoading = replyLoads.contains(comment.rpid),
                                    onAvatarClick = { onOpenSpace(comment.mid) },
                                    onLike = { toggleCommentLike(comment) },
                                    onReply = {
                                        replyTarget = comment
                                        LoginPrompt.dismiss()
                                    },
                                    onDelete = { deleteTarget = comment },
                                    onLoadReplies = { refresh -> loadReplies(comment, refresh) },
                                    onReplyToReply = { reply ->
                                        replyTarget = reply
                                    },
                                    onLikeReply = { reply -> toggleCommentLike(reply) },
                                    onDeleteReply = { reply -> deleteTarget = reply },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                        items(comments, key = { it.rpid }) { comment ->
                            CommentThread(
                                comment = comment,
                                isTop = false,
                                ownerMid = ownerMid,
                                accountMid = accountMid,
                                loggedIn = loggedIn,
                                replyLoading = replyLoads.contains(comment.rpid),
                                onAvatarClick = { onOpenSpace(comment.mid) },
                                onLike = { toggleCommentLike(comment) },
                                onReply = {
                                    replyTarget = comment
                                },
                                onDelete = { deleteTarget = comment },
                                onLoadReplies = { refresh -> loadReplies(comment, refresh) },
                                onReplyToReply = { reply ->
                                    replyTarget = reply
                                },
                                onLikeReply = { reply -> toggleCommentLike(reply) },
                                onDeleteReply = { reply -> deleteTarget = reply },
                                modifier = Modifier.animateItem()
                            )
                        }
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (loadingMore) {
                                    CircularProgressIndicator(
                                        color = appTextMuted(),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else if (hasMore) {
                                    TextButton(onClick = { loadMore() }) {
                                        Text("加载更多评论", color = appTextMuted(), fontSize = 12.sp)
                                    }
                                } else {
                                    Text(
                                        text = "没有更多评论了",
                                        color = appText().copy(alpha = 0.3f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = appDivider())
            val target = replyTarget
            AnimatedVisibility(
                visible = target != null,
                enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(150)) + fadeOut(tween(120))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "回复 @${target?.userName.orEmpty()}",
                        color = BiliAccent,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "取消",
                        color = appTextMuted(),
                        fontSize = 11.sp,
                        modifier = Modifier.noRippleClickable { replyTarget = null }
                    )
                }
            }

            if (loggedIn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RemoteImage(
                        url = account?.face.orEmpty(),
                        contentDescription = account?.name.orEmpty(),
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                        targetWidth = 96,
                        placeholder = appField()
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it.take(500) },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(appField())
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        textStyle = TextStyle(color = appText(), fontSize = 14.sp),
                        cursorBrush = SolidColor(BiliAccent),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        decorationBox = { inner ->
                            Box {
                                if (input.isEmpty()) {
                                    Text(
                                        text = if (replyTarget == null) "发一条友善的评论" else "回复 @${replyTarget?.userName}",
                                        color = appTextMuted(),
                                        fontSize = 14.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    Spacer(Modifier.width(6.dp))
                    val sendActive = input.isNotBlank() && !sending
                    val sendBackground by animateColorAsState(
                        targetValue = if (sendActive) BiliAccent else appChip(),
                        animationSpec = tween(180),
                        label = "sendBackground"
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(sendBackground)
                            .pressClickable(enabled = sendActive, pressedScale = 0.94f) { sendComment() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (sending) {
                            CircularProgressIndicator(
                                color = appTextMuted(),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(17.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送评论",
                                tint = if (sendActive) Color.White else appTextMuted(),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(appField())
                        .pressClickable(pressedScale = 0.97f) { LoginPrompt.show() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = appTextMuted(),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "登录后参与评论",
                        color = appTextMuted(),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    val deleting = deleteTarget
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = appSurface(),
            titleContentColor = appText(),
            textContentColor = appTextMuted(),
            title = { Text("删除评论") },
            text = { Text("确定删除这条评论吗？") },
            confirmButton = {
                TextButton(onClick = {
                    val target = deleting
                    deleteTarget = null
                    deleteComment(target)
                }) {
                    Text("删除", color = BiliAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消", color = appTextMuted())
                }
            }
        )
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (selected) appAccentSoft() else Color.Transparent,
        animationSpec = tween(180),
        label = "sortBackground"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) BiliAccent else appTextMuted(),
        animationSpec = tween(180),
        label = "sortText"
    )
    Text(
        text = label,
        color = textColor,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .pressClickable(pressedScale = 0.96f) { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp)
    )
}

@Composable
private fun CommentsSkeleton() {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        repeat(6) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                SkeletonBox(Modifier.size(36.dp), CircleShape)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBox(Modifier.width(88.dp).height(12.dp))
                    Spacer(Modifier.height(8.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.94f).height(13.dp))
                    Spacer(Modifier.height(6.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.58f).height(13.dp))
                    Spacer(Modifier.height(7.dp))
                    SkeletonBox(Modifier.width(64.dp).height(9.dp))
                }
            }
        }
    }
}

@Composable
private fun CommentThread(
    comment: CommentItem,
    isTop: Boolean,
    ownerMid: Long,
    accountMid: Long,
    loggedIn: Boolean,
    replyLoading: Boolean,
    onAvatarClick: () -> Unit,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onLoadReplies: (Boolean) -> Unit,
    onReplyToReply: (CommentItem) -> Unit,
    onLikeReply: (CommentItem) -> Unit,
    onDeleteReply: (CommentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        CommentRow(
            comment = comment,
            isReply = false,
            isUp = comment.mid == ownerMid && ownerMid > 0L,
            isTop = isTop || comment.isUpTop,
            canDelete = loggedIn && comment.mid == accountMid && accountMid > 0L,
            onAvatarClick = onAvatarClick,
            onLike = onLike,
            onReply = onReply,
            onDelete = onDelete
        )
        val replies = comment.replies
        AnimatedVisibility(
            visible = replies.isNotEmpty(),
            enter = expandVertically(tween(220)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(140))
        ) {
            Column(Modifier.padding(start = 52.dp)) {
                replies.forEach { reply ->
                    CommentRow(
                        comment = reply,
                        isReply = true,
                        isUp = reply.mid == ownerMid && ownerMid > 0L,
                        isTop = false,
                        canDelete = loggedIn && reply.mid == accountMid && accountMid > 0L,
                        onAvatarClick = onAvatarClick,
                        onLike = { onLikeReply(reply) },
                        onReply = { onReplyToReply(reply) },
                        onDelete = { onDeleteReply(reply) }
                    )
                }
                if (comment.replyCount > replies.size.toLong()) {
                    TextButton(
                        onClick = { onLoadReplies(replies.isEmpty()) },
                        enabled = !replyLoading
                    ) {
                        if (replyLoading) {
                            CircularProgressIndicator(
                                color = appTextMuted(),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Text(
                                text = if (replies.isEmpty()) {
                                    "查看 ${comment.replyCount} 条回复"
                                } else {
                                    "加载更多回复"
                                },
                                color = appTextMuted(),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
        if (replies.isEmpty() && comment.replyCount > 0L) {
            TextButton(
                onClick = { onLoadReplies(true) },
                enabled = !replyLoading,
                modifier = Modifier.padding(start = 52.dp)
            ) {
                if (replyLoading) {
                    CircularProgressIndicator(
                        color = appTextMuted(),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    Text(
                        text = "查看 ${comment.replyCount} 条回复",
                        color = appTextMuted(),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommentItem,
    isReply: Boolean,
    isUp: Boolean,
    isTop: Boolean,
    canDelete: Boolean,
    onAvatarClick: () -> Unit,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isReply) 0.dp else 20.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = 10.dp
            )
    ) {
        RemoteImage(
            url = comment.avatar,
            contentDescription = comment.userName,
            modifier = Modifier
                .size(if (isReply) 30.dp else 40.dp)
                .clip(CircleShape)
                .pressClickable(pressedScale = 0.95f) { onAvatarClick() },
            targetWidth = 96,
            placeholder = appField()
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.userName,
                    color = if (isUp) BiliAccent else appText(),
                    fontSize = if (isReply) 12.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isUp) {
                    Spacer(Modifier.width(5.dp))
                    MiniBadge("UP", BiliAccent)
                }
                if (isTop) {
                    Spacer(Modifier.width(4.dp))
                    MiniBadge("置顶", appTextMuted())
                }
                if (comment.isUpLiked) {
                    Spacer(Modifier.width(4.dp))
                    MiniBadge("UP主赞", appTextMuted())
                }
            }
            Spacer(Modifier.height(3.dp))
            CommentBody(comment = comment, isReply = isReply)
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTimeAgo(comment.ctime),
                    color = appTextMuted(),
                    fontSize = 11.sp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "回复",
                    color = appTextMuted(),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .pressClickable(pressedScale = 0.96f) { onReply() }
                        .padding(vertical = 3.dp)
                )
                if (canDelete) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "删除",
                        color = appTextMuted(),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .pressClickable(pressedScale = 0.96f) { onDelete() }
                            .padding(vertical = 3.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        val likePop = rememberPopScale(comment.liked)
        val likeTint by animateColorAsState(
            targetValue = if (comment.liked) BiliAccent else appTextMuted(),
            animationSpec = tween(180),
            label = "commentLikeTint"
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(32.dp)
                    .pressClickable(pressedScale = 0.9f) { onLike() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (comment.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (comment.liked) "取消点赞评论" else "点赞评论",
                    tint = likeTint,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            scaleX = likePop
                            scaleY = likePop
                        }
                )
            }
            AnimatedSwapText(
                text = formatCount(comment.like),
                style = TextStyle(color = appTextMuted(), fontSize = 10.sp)
            )
        }
    }
}

/** 评论正文：文本内联 B 站表情包 + 图片评论缩略图。 */
@Composable
private fun CommentBody(comment: CommentItem, isReply: Boolean) {
    val fontSize = if (isReply) 13.sp else 14.sp
    val textColor = appText()
    if (comment.content.isNotBlank()) {
        if (comment.emotes.isEmpty()) {
            Text(
                text = comment.content,
                color = textColor,
                fontSize = fontSize,
                lineHeight = 22.sp,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            val annotated = remember(comment.content, comment.emotes) {
                buildAnnotatedString {
                    val text = comment.content
                    val regex = Regex(
                        comment.emotes.keys.sortedByDescending { it.length }
                            .joinToString("|") { Regex.escape(it) }
                    )
                    var last = 0
                    regex.findAll(text).forEach { match ->
                        append(text.substring(last, match.range.first))
                        appendInlineContent(match.value, match.value)
                        last = match.range.last + 1
                    }
                    append(text.substring(last))
                }
            }
            val inlineContent = remember(comment.emotes) {
                comment.emotes.mapValues { (key, emote) ->
                    val size = if (emote.big) 40.sp else 20.sp
                    InlineTextContent(
                        Placeholder(
                            width = size,
                            height = size,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                        )
                    ) {
                        RemoteImage(
                            url = emote.url,
                            contentDescription = key,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            targetWidth = if (emote.big) 192 else 96,
                            placeholder = Color.Transparent
                        )
                    }
                }
            }
            Text(
                text = annotated,
                inlineContent = inlineContent,
                color = textColor,
                fontSize = fontSize,
                lineHeight = 22.sp,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    CommentPictures(comment.pictures)
}

@Composable
private fun CommentPictures(pictures: List<CommentPicture>) {
    if (pictures.isEmpty()) return
    val context = LocalContext.current
    val placeholder = appField()
    Spacer(Modifier.height(6.dp))
    if (pictures.size == 1) {
        val pic = pictures[0]
        val ratio = if (pic.width > 0 && pic.height > 0) {
            (pic.width.toFloat() / pic.height.toFloat()).coerceIn(0.75f, 1.78f)
        } else {
            1f
        }
        Box(
            Modifier
                .width(180.dp)
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(12.dp))
                .pressClickable(pressedScale = 0.98f) { openUrl(context, pic.url) }
        ) {
            RemoteImage(
                url = pic.url,
                contentDescription = "评论图片",
                modifier = Modifier.fillMaxSize(),
                targetWidth = 480,
                placeholder = placeholder
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pictures.take(3).forEachIndexed { index, pic ->
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pressClickable(pressedScale = 0.95f) { openUrl(context, pic.url) }
                ) {
                    RemoteImage(
                        url = pic.url,
                        contentDescription = "评论图片",
                        modifier = Modifier.fillMaxSize(),
                        targetWidth = 240,
                        placeholder = placeholder
                    )
                    if (index == 2 && pictures.size > 3) {
                        Box(
                            Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+${pictures.size - 3}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniBadge(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}

private fun mergeComments(
    existing: List<CommentItem>,
    incoming: List<CommentItem>
): List<CommentItem> {
    if (existing.isEmpty()) return incoming
    val seen = existing.mapTo(HashSet()) { it.rpid }
    return existing + incoming.filter { seen.add(it.rpid) }
}

private fun updateCommentList(
    list: List<CommentItem>,
    rpid: Long,
    transform: (CommentItem) -> CommentItem
): List<CommentItem> = list.map { comment ->
    if (comment.rpid == rpid) {
        transform(comment)
    } else if (comment.replies.isNotEmpty()) {
        comment.copy(replies = updateCommentList(comment.replies, rpid, transform))
    } else {
        comment
    }
}

private fun removeComment(list: List<CommentItem>, rpid: Long): List<CommentItem> =
    list.filterNot { it.rpid == rpid }
        .map { comment ->
            if (comment.replies.isEmpty()) {
                comment
            } else {
                comment.copy(replies = removeComment(comment.replies, rpid))
            }
        }

private fun handleCommentError(error: Exception, fallback: String) {
    if (error is BiliApi.BiliException && error.code == -101) {
        LoginPrompt.show()
        UiMessages.show("登录状态已失效，请重新登录")
    } else {
        UiMessages.show(error.message?.takeIf { it.isNotBlank() } ?: fallback)
    }
}

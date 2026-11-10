package com.biliflow.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliflow.app.R
import com.biliflow.app.data.AccountUser
import com.biliflow.app.data.SocialState
import com.biliflow.app.data.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinSheet(
    item: VideoItem,
    user: AccountUser?,
    social: SocialState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Boolean) -> Unit
) {
    val remaining = (2 - (social.coinCount + social.coinDelta)).coerceAtLeast(0)
    var count by remember { mutableIntStateOf(if (remaining >= 1) 1 else 0) }
    var alsoLike by remember { mutableStateOf(!social.liked) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = appSurface(),
        contentColor = appText(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = appTextMuted().copy(alpha = 0.45f)) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("投币支持", color = appText(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                text = "给「${item.ownerName}」的创作一点鼓励",
                color = appTextMuted(),
                fontSize = 12.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (user != null) {
                    "硬币余额 ${user.coins} · 本视频已投 ${social.coinCount + social.coinDelta} 枚"
                } else {
                    "登录后可投币"
                },
                color = appTextMuted(),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(20.dp))

            Text("选择投币数量", color = appText(), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(1, 2).forEach { value ->
                    val selected = count == value
                    val enabled = value <= remaining
                    val background by animateColorAsState(
                        targetValue = if (selected) appAccentSoft() else appSurfaceRaised(),
                        animationSpec = tween(180),
                        label = "coinOptionBackground"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (selected) BiliAccent else appDivider(),
                        animationSpec = tween(180),
                        label = "coinOptionBorder"
                    )
                    val iconPop = rememberPopScale(selected)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(112.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(background)
                            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                            .pressClickable(enabled = enabled, pressedScale = 0.96f) { count = value }
                    ) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_coin),
                                contentDescription = null,
                                tint = if (enabled) BiliAccent else appTextMuted().copy(alpha = 0.45f),
                                modifier = Modifier
                                    .size(28.dp)
                                    .graphicsLayer {
                                        scaleX = iconPop
                                        scaleY = iconPop
                                    }
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "$value 枚硬币",
                                color = if (enabled) appText() else appTextMuted().copy(alpha = 0.45f),
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(BiliAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "已选择",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(appSurfaceRaised())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("同时点赞", color = appText(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text("投币时顺便点个赞", color = appTextMuted(), fontSize = 11.sp)
                }
                Switch(
                    checked = alsoLike,
                    onCheckedChange = { alsoLike = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BiliAccent,
                        uncheckedThumbColor = appTextMuted(),
                        uncheckedTrackColor = appField()
                    )
                )
            }

            Spacer(Modifier.height(20.dp))
            AppPrimaryButton(
                text = if (remaining > 0) "确认投币" else "本视频已投满",
                onClick = {
                    if (count > 0 && remaining > 0) {
                        onConfirm(count.coerceAtMost(remaining), alsoLike)
                    }
                },
                enabled = count > 0 && remaining > 0,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

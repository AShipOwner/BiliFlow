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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF4A4A4A)) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp)
        ) {
            Text(
                text = "给「${item.ownerName}」投币",
                color = appText(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (user != null) {
                    "硬币余额 ${user.coins} · 本视频已投 ${social.coinCount + social.coinDelta} 枚"
                } else {
                    "登录后可投币"
                },
                color = appTextMuted(),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(1, 2).forEach { value ->
                    val selected = count == value
                    val enabled = value <= remaining
                    val background by animateColorAsState(
                        targetValue = if (selected) BiliAccent.copy(alpha = 0.16f) else appSurface(),
                        animationSpec = tween(200),
                        label = "coinOptionBackground"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (selected) BiliAccent else Color.Transparent,
                        animationSpec = tween(200),
                        label = "coinOptionBorder"
                    )
                    val iconPop = rememberPopScale(selected)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(background)
                            .border(
                                width = 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .pressClickable(enabled = enabled, pressedScale = 0.94f) { count = value }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_coin),
                                contentDescription = null,
                                tint = if (enabled) BiliAccent else appTextMuted(),
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = iconPop
                                        scaleY = iconPop
                                    }
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "$value 枚",
                                color = if (enabled) appText() else appTextMuted(),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("同时点赞", color = appText(), fontSize = 13.sp)
                    Text("投币时顺便点个赞", color = appTextMuted(), fontSize = 10.sp)
                }
                Switch(
                    checked = alsoLike,
                    onCheckedChange = { alsoLike = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = appText(),
                        checkedTrackColor = BiliAccent,
                        uncheckedThumbColor = appTextMuted(),
                        uncheckedTrackColor = Color(0xFF303030)
                    )
                )
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (count > 0 && remaining > 0) {
                        onConfirm(count.coerceAtMost(remaining), alsoLike)
                    }
                },
                enabled = count > 0 && remaining > 0,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(23.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BiliAccent,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF303030),
                    disabledContentColor = appTextMuted()
                )
            ) {
                Text(
                    text = if (remaining > 0) "确认投币" else "本视频已投满",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

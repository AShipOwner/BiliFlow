package com.biliflow.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 通用播放设置面板：倍速 / 清晰度。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSelectSheet(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BiliSurface,
        contentColor = Color.White
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            options.forEach { option ->
                val active = option == selected
                val rowBackground by animateColorAsState(
                    targetValue = if (active) Color(0xFF2A2A31) else Color.Transparent,
                    animationSpec = tween(180),
                    label = "optionBackground"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(rowBackground)
                        .pressClickable(pressedScale = 0.97f) { onSelect(option) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option,
                        color = if (active) BiliAccent else Color.White,
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    AnimatedVisibility(
                        visible = active,
                        enter = scaleIn(tween(180), initialScale = 0.6f) + fadeIn(tween(150)),
                        exit = scaleOut(tween(120), targetScale = 0.6f) + fadeOut(tween(100))
                    ) {
                        Text("当前", color = BiliAccent, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

val PLAYER_SPEED_OPTIONS = listOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")

fun speedFromLabel(label: String): Float = label.removeSuffix("x").toFloatOrNull() ?: 1f

fun speedLabelOf(speed: Float): String = when (speed) {
    0.5f -> "0.5x"
    0.75f -> "0.75x"
    1f -> "1.0x"
    1.25f -> "1.25x"
    1.5f -> "1.5x"
    2f -> "2.0x"
    else -> "${speed}x"
}

val PLAYER_QUALITY_OPTIONS = listOf("自动", "1080P", "720P", "480P", "360P")

fun qualityModeOf(label: String): com.biliflow.app.data.QualityMode =
    com.biliflow.app.data.QualityMode.entries.firstOrNull { it.label == label }
        ?: com.biliflow.app.data.QualityMode.AUTO

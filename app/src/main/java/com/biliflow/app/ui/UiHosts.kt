package com.biliflow.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliflow.app.data.LoginPrompt
import com.biliflow.app.data.UiMessages
import kotlinx.coroutines.delay

@Composable
fun UiHosts() {
    val loginVisible by LoginPrompt.visible.collectAsStateCompat()
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        UiMessages.messages.collect { message = it }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2400)
            message = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = message != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 76.dp),
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { it / 4 }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().noRippleClickable { message = null },
                shape = RoundedCornerShape(16.dp),
                color = if (LocalAppDark.current) Color(0xFFF0F1F4) else Color(0xFF24262C),
                contentColor = if (LocalAppDark.current) Color(0xFF191B20) else Color.White,
                shadowElevation = 8.dp
            ) {
                Text(
                    text = message.orEmpty(),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (loginVisible) {
        LoginSheet(onDismiss = { LoginPrompt.dismiss() })
    }
}

package com.biliflow.app.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.biliflow.app.data.LoginPrompt
import com.biliflow.app.data.UiMessages

@Composable
fun UiHosts() {
    val context = LocalContext.current
    val loginVisible by LoginPrompt.visible.collectAsStateCompat()

    LaunchedEffect(Unit) {
        UiMessages.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    if (loginVisible) {
        LoginSheet(onDismiss = { LoginPrompt.dismiss() })
    }
}

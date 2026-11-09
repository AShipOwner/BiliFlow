package com.biliflow.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.biliflow.app.data.AuthManager
import com.biliflow.app.data.BiliApi
import com.biliflow.app.data.ImageLoader
import com.biliflow.app.data.LocalStore
import com.biliflow.app.data.NetworkMonitor
import com.biliflow.app.ui.BiliFlowApp
import com.biliflow.app.ui.BiliFlowTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        LocalStore.init(applicationContext)
        ImageLoader.init(applicationContext)
        AuthManager.init(applicationContext)
        NetworkMonitor.init(applicationContext)
        lifecycleScope.launch { BiliApi.ensureBuvid() }

        setContent {
            BiliFlowTheme {
                BiliFlowApp()
            }
        }
    }
}

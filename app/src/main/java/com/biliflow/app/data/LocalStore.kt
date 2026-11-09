package com.biliflow.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LocalStore {
    private var prefs: SharedPreferences? = null

    private val _qualityMode = MutableStateFlow(QualityMode.AUTO)
    val qualityMode: StateFlow<QualityMode> = _qualityMode.asStateFlow()

    private val _danmakuEnabled = MutableStateFlow(true)
    val danmakuEnabled: StateFlow<Boolean> = _danmakuEnabled.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _stretchFill = MutableStateFlow(false)
    val stretchFill: StateFlow<Boolean> = _stretchFill.asStateFlow()

    /** 0=跟随系统 1=浅色 2=深色 */
    private val _themeMode = MutableStateFlow(0)
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    /** 本机编辑的主页资料（B 站没有开放这些写接口，先本机生效） */
    private val _localName = MutableStateFlow("")
    val localName: StateFlow<String> = _localName.asStateFlow()
    private val _localSign = MutableStateFlow("")
    val localSign: StateFlow<String> = _localSign.asStateFlow()
    private val _localAvatar = MutableStateFlow("")
    val localAvatar: StateFlow<String> = _localAvatar.asStateFlow()
    private val _localBanner = MutableStateFlow("")
    val localBanner: StateFlow<String> = _localBanner.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext
            .getSharedPreferences("bili_flow", Context.MODE_PRIVATE)
        prefs = store

        val savedMode = store.getString("quality_mode", null)
        _qualityMode.value = QualityMode.entries.firstOrNull { it.name == savedMode }
            ?: when (store.getInt("quality", 64)) {
                80 -> QualityMode.Q1080
                64 -> QualityMode.Q720
                32 -> QualityMode.Q480
                16 -> QualityMode.Q360
                else -> QualityMode.AUTO
            }

        _danmakuEnabled.value = store.getBoolean("danmaku_enabled", true)
        _playbackSpeed.value = store.getFloat("playback_speed", 1f).coerceIn(0.5f, 3f)
        _stretchFill.value = store.getBoolean("stretch_fill", false)
        _themeMode.value = store.getInt("theme_mode", 0)
        _localName.value = store.getString("local_name", "").orEmpty()
        _localSign.value = store.getString("local_sign", "").orEmpty()
        _localAvatar.value = store.getString("local_avatar", "").orEmpty()
        _localBanner.value = store.getString("local_banner", "").orEmpty()
        _searchHistory.value = store.getString("search_history", "")
            .orEmpty()
            .split("\u001F")
            .filter { it.isNotBlank() }
    }

    fun setQualityMode(mode: QualityMode) {
        _qualityMode.value = mode
        prefs?.edit()?.putString("quality_mode", mode.name)?.apply()
    }

    fun setDanmakuEnabled(enabled: Boolean) {
        _danmakuEnabled.value = enabled
        prefs?.edit()?.putBoolean("danmaku_enabled", enabled)?.apply()
    }

    fun setPlaybackSpeed(speed: Float) {
        val value = speed.coerceIn(0.5f, 3f)
        _playbackSpeed.value = value
        prefs?.edit()?.putFloat("playback_speed", value)?.apply()
    }

    fun setThemeMode(mode: Int) {
        _themeMode.value = mode
        prefs?.edit()?.putInt("theme_mode", mode)?.apply()
    }

    fun setLocalProfile(name: String, sign: String, avatar: String, banner: String) {
        _localName.value = name
        _localSign.value = sign
        _localAvatar.value = avatar
        _localBanner.value = banner
        prefs?.edit()
            ?.putString("local_name", name)
            ?.putString("local_sign", sign)
            ?.putString("local_avatar", avatar)
            ?.putString("local_banner", banner)
            ?.apply()
    }

    fun setStretchFill(enabled: Boolean) {
        _stretchFill.value = enabled
        prefs?.edit()?.putBoolean("stretch_fill", enabled)?.apply()
    }

    fun addSearchHistory(keyword: String) {
        val value = keyword.trim()
        if (value.isBlank()) return
        val next = (listOf(value) + _searchHistory.value.filterNot { it == value }).take(8)
        _searchHistory.value = next
        prefs?.edit()?.putString("search_history", next.joinToString("\u001F"))?.apply()
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        prefs?.edit()?.remove("search_history")?.apply()
    }
}

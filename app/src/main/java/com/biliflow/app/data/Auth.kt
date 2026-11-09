package com.biliflow.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val user: AccountUser) : AuthState
    data class Error(val message: String) : AuthState
}

data class QrLoginInfo(
    val url: String,
    val qrKey: String
)

sealed interface QrPollResult {
    data object Waiting : QrPollResult
    data object Scanned : QrPollResult
    data object Expired : QrPollResult
    data class Success(val user: AccountUser) : QrPollResult
    data class Failure(val message: String) : QrPollResult
}

object LoginPrompt {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun show() {
        _visible.value = true
    }

    fun dismiss() {
        _visible.value = false
    }
}

object AuthManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: SharedPreferences

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    @Volatile
    var csrf: String = ""
        private set

    @Volatile
    var mid: Long = 0L
        private set

    val user: AccountUser?
        get() = (_state.value as? AuthState.LoggedIn)?.user

    val isLoggedIn: Boolean
        get() = _state.value is AuthState.LoggedIn

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("bili_auth", Context.MODE_PRIVATE)
        csrf = prefs.getString("csrf", "").orEmpty()
        mid = prefs.getLong("mid", 0L)
        val cookie = prefs.getString("cookie", "").orEmpty()

        if (cookie.isBlank()) {
            _state.value = AuthState.LoggedOut
            return
        }

        Http.cookie = cookie
        val cached = readCachedUser()
        _state.value = AuthState.LoggedIn(cached ?: AccountUser(mid = mid))
        scope.launch { refreshAccount() }
    }

    suspend fun generateQr(): QrLoginInfo {
        val response = JSONObject(
            Http.getAsync(
                "https://passport.bilibili.com/x/passport-login/web/qrcode/generate" +
                    "?source=main-fe-header",
                referer = "https://www.bilibili.com/"
            )
        )
        if (response.optInt("code", -1) != 0) {
            throw IllegalStateException(response.optString("message", "二维码生成失败"))
        }
        val data = response.optJSONObject("data")
            ?: throw IllegalStateException("二维码数据为空")
        val url = data.optString("url")
        val key = data.optString("qrcode_key")
        if (url.isBlank() || key.isBlank()) throw IllegalStateException("二维码数据不完整")
        return QrLoginInfo(url = url, qrKey = key)
    }

    suspend fun pollQr(key: String): QrPollResult {
        return try {
            val pollResponse = Http.getTextWithCookiesAsync(
                "https://passport.bilibili.com/x/passport-login/web/qrcode/poll" +
                    "?qrcode_key=${Uri.encode(key)}&source=main-fe-header",
                referer = "https://www.bilibili.com/"
            )
            val response = JSONObject(pollResponse.text)
            val data = response.optJSONObject("data")
            val topCode = response.optInt("code", -1)
            val dataCode = data?.optInt("code", 0) ?: 0
            val code = when {
                topCode != 0 -> topCode
                dataCode != 0 -> dataCode
                else -> 0
            }
            when (code) {
                0 -> {
                    val redirectUrl = data?.optString("url").orEmpty()
                    if (redirectUrl.isBlank()) {
                        QrPollResult.Failure("登录信息为空，请刷新二维码重试")
                    } else {
                        val account = saveSession(redirectUrl, pollResponse.setCookies)
                        if (account != null) {
                            QrPollResult.Success(account)
                        } else {
                            QrPollResult.Failure("登录成功，但登录态解析失败，请刷新二维码重试")
                        }
                    }
                }

                86101 -> QrPollResult.Waiting
                86090 -> QrPollResult.Scanned
                86038 -> QrPollResult.Expired
                else -> QrPollResult.Failure(
                    response.optString("message")
                        .ifBlank { data?.optString("message").orEmpty() }
                        .ifBlank { "登录失败（code=$code）" }
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("BiliFlow", "qr poll failed", error)
            QrPollResult.Failure(error.message ?: "登录轮询失败")
        }
    }

    suspend fun refreshAccount(): AccountUser? {
        return try {
            val response = JSONObject(
                Http.getAsync(
                    "https://api.bilibili.com/x/web-interface/nav",
                    referer = "https://www.bilibili.com/"
                )
            )
            val code = response.optInt("code", -1)
            val data = response.optJSONObject("data")
            if (code == -101 || data?.optBoolean("isLogin", false) != true) {
                clearSession()
                return null
            }
            if (code != 0) {
                if (_state.value !is AuthState.LoggedIn) {
                    _state.value = AuthState.Error(response.optString("message", "账号信息加载失败"))
                }
                return null
            }
            val vipLabel = when (val label = data.opt("vip_label")) {
                is JSONObject -> label.optString("text")
                is String -> label
                else -> ""
            }
            val account = AccountUser(
                mid = data.optLong("mid", mid),
                name = data.optString("uname"),
                face = Http.normalizeUrl(data.optString("face")),
                level = data.optJSONObject("level_info")?.optInt("current_level", 0) ?: 0,
                vipStatus = data.optInt("vipStatus", 0),
                vipType = data.optInt("vipType", 0),
                vipLabel = vipLabel,
                coins = data.optInt("money", 0),
                following = data.optInt("following", 0),
                follower = data.optInt("follower", 0)
            )
            mid = account.mid
            cacheUser(account)
            _state.value = AuthState.LoggedIn(account)
            account
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("BiliFlow", "refresh account failed", error)
            if (_state.value !is AuthState.LoggedIn) {
                _state.value = AuthState.Error(error.message ?: "账号信息加载失败")
            }
            null
        }
    }

    fun logout() {
        clearSession()
    }

    /** 从 WebView 的 cookie 串恢复登录态（网页登录兜底） */
    suspend fun loginFromWebCookie(cookieString: String): AccountUser? {
        val parts = cookieString.split(";").map { it.trim() }.filter { it.contains("=") }
        if (parts.none { it.startsWith("SESSDATA=") }) return null
        return saveSession("", parts)
    }

    /** 手机号验证码登录：发送短信 */
    suspend fun sendSmsCode(phone: String) {
        val response = Http.postFormWithCookiesAsync(
            "https://passport.bilibili.com/x/passport-login/web/sms/send",
            mapOf(
                "tel" to phone,
                "cid" to "86",
                "source" to "main-fe",
                "csrf" to csrf
            ),
            referer = "https://passport.bilibili.com/login"
        )
        val json = org.json.JSONObject(response.text)
        val code = json.optInt("code", -1)
        if (code != 0) {
            throw IllegalStateException(json.optString("message", "验证码发送失败"))
        }
    }

    /** 手机号验证码登录 */
    suspend fun loginWithSms(phone: String, code: String): AccountUser? {
        val response = Http.postFormWithCookiesAsync(
            "https://passport.bilibili.com/x/passport-login/web/login/sms",
            mapOf(
                "cid" to "86",
                "tel" to phone,
                "code" to code,
                "source" to "main-fe",
                "csrf" to csrf
            ),
            referer = "https://passport.bilibili.com/login"
        )
        val json = org.json.JSONObject(response.text)
        if (json.optInt("code", -1) != 0) {
            throw IllegalStateException(json.optString("message", "登录失败"))
        }
        val redirect = json.optJSONObject("data")?.optString("url").orEmpty()
        return saveSession(redirect, response.setCookies)
    }

    fun ensureLogin(): Boolean {
        if (isLoggedIn) return true
        LoginPrompt.show()
        return false
    }

    val isVip: Boolean
        get() = user?.isVip == true

    private suspend fun saveSession(
        redirectUrl: String,
        setCookies: List<String>
    ): AccountUser? {
        val urlParams = LinkedHashMap<String, String>()
        val allCookies = LinkedHashMap<String, String>()

        fun collectUrlParams(value: String?) {
            if (value.isNullOrBlank()) return
            runCatching {
                val uri = Uri.parse(value)
                uri.queryParameterNames.forEach { name ->
                    uri.getQueryParameter(name)?.takeIf { it.isNotBlank() }?.let {
                        urlParams[name] = it
                    }
                }
                val fragment = uri.fragment
                if (!fragment.isNullOrBlank()) {
                    val fragmentUri = Uri.parse(
                        if (fragment.startsWith("?")) fragment else "?$fragment"
                    )
                    fragmentUri.queryParameterNames.forEach { name ->
                        fragmentUri.getQueryParameter(name)?.takeIf { it.isNotBlank() }?.let {
                            urlParams[name] = it
                        }
                    }
                }
            }
        }

        collectUrlParams(redirectUrl)
        setCookies.forEach { header ->
            val firstPart = header.substringBefore(';').trim()
            val separator = firstPart.indexOf('=')
            if (separator > 0) {
                val name = firstPart.substring(0, separator).trim()
                val value = firstPart.substring(separator + 1).trim()
                if (name.isNotBlank() && value.isNotBlank()) {
                    allCookies[name] = value
                }
            }
        }

        val authNames = listOf(
            "SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid",
            "buvid3", "buvid4", "b_nut", "bili_ticket"
        )
        authNames.forEach { name ->
            urlParams[name]?.takeIf { it.isNotBlank() }?.let { allCookies[name] = it }
        }

        val sessData = allCookies["SESSDATA"].orEmpty()
        Log.d(
            "BiliFlow",
            "qr login cookie names=${allCookies.keys.joinToString(",")} " +
                "sessData=${sessData.isNotBlank()}"
        )
        if (sessData.isBlank()) return null

        val biliJct = allCookies["bili_jct"].orEmpty()
        val dedeUserId = allCookies["DedeUserID"].orEmpty()
        val authCookie = allCookies.entries.joinToString("; ") { (name, value) ->
            "$name=$value"
        }

        runCatching { Wbi.ensureSession() }
        Http.cookie = mergeCookies(Http.cookie, authCookie)
        csrf = biliJct
        mid = dedeUserId.toLongOrNull() ?: 0L
        prefs.edit()
            .putString("cookie", Http.cookie)
            .putString("csrf", csrf)
            .putLong("mid", mid)
            .apply()

        val account = refreshAccount()
        if (account != null) return account
        if (Http.cookie.isNotBlank()) {
            val fallback = AccountUser(mid = mid)
            _state.value = AuthState.LoggedIn(fallback)
            cacheUser(fallback)
            return fallback
        }
        return null
    }

    private fun clearSession() {
        csrf = ""
        mid = 0L
        Http.cookie = ""
        Wbi.invalidate()
        runCatching {
            val cookies = android.webkit.CookieManager.getInstance()
            cookies.removeAllCookies(null)
            cookies.flush()
        }
        prefs.edit()
            .remove("cookie")
            .remove("csrf")
            .remove("mid")
            .remove("user_name")
            .remove("user_face")
            .remove("user_level")
            .remove("user_vip_status")
            .remove("user_vip_type")
            .remove("user_vip_label")
            .remove("user_coins")
            .remove("user_following")
            .remove("user_follower")
            .apply()
        _state.value = AuthState.LoggedOut
    }

    private fun mergeCookies(existing: String, incoming: String): String {
        val map = LinkedHashMap<String, String>()
        fun add(cookie: String) {
            cookie.split(";").forEach { part ->
                val trimmed = part.trim()
                if (trimmed.isBlank()) return@forEach
                val index = trimmed.indexOf('=')
                if (index <= 0) return@forEach
                map[trimmed.substring(0, index)] = trimmed.substring(index + 1)
            }
        }
        add(existing)
        add(incoming)
        return map.entries.joinToString("; ") { (key, value) -> "$key=$value" }
    }

    private fun cacheUser(account: AccountUser) {
        prefs.edit()
            .putLong("mid", account.mid)
            .putString("user_name", account.name)
            .putString("user_face", account.face)
            .putInt("user_level", account.level)
            .putInt("user_vip_status", account.vipStatus)
            .putInt("user_vip_type", account.vipType)
            .putString("user_vip_label", account.vipLabel)
            .putInt("user_coins", account.coins)
            .putInt("user_following", account.following)
            .putInt("user_follower", account.follower)
            .apply()
    }

    private fun readCachedUser(): AccountUser? {
        val cachedMid = prefs.getLong("mid", 0L)
        val name = prefs.getString("user_name", "").orEmpty()
        if (cachedMid <= 0L && name.isBlank()) return null
        return AccountUser(
            mid = cachedMid,
            name = name,
            face = prefs.getString("user_face", "").orEmpty(),
            level = prefs.getInt("user_level", 0),
            vipStatus = prefs.getInt("user_vip_status", 0),
            vipType = prefs.getInt("user_vip_type", 0),
            vipLabel = prefs.getString("user_vip_label", "").orEmpty(),
            coins = prefs.getInt("user_coins", 0),
            following = prefs.getInt("user_following", 0),
            follower = prefs.getInt("user_follower", 0)
        )
    }
}

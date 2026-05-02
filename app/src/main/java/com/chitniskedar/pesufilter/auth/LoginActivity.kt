package com.chitniskedar.pesufilter.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chitniskedar.pesufilter.R
import com.chitniskedar.pesufilter.databinding.ActivityLoginBinding
import com.chitniskedar.pesufilter.parser.HtmlParser
import com.chitniskedar.pesufilter.ui.MainActivity
import com.chitniskedar.pesufilter.ui.OnboardingActivity
import com.chitniskedar.pesufilter.utils.PreferencesManager
import com.chitniskedar.pesufilter.worker.BackgroundSyncService
import org.json.JSONArray
import java.net.URI

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var preferencesManager: PreferencesManager
    private val htmlParser = HtmlParser()
    private val loginWatcherHandler = Handler(Looper.getMainLooper())
    private var completedLogin = false
    private val loginWatcher = object : Runnable {
        override fun run() {
            val currentUrl = binding.webViewPortal.url ?: PESU_PORTAL_URL
            inspectPageForLoginSuccess(currentUrl)
            loginWatcherHandler.postDelayed(this, LOGIN_WATCH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager(this)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonRetry.setOnClickListener {
            loadPortal(forceFreshSession = true)
        }

        configureWebView()
        loadPortal(forceFreshSession = intent.getBooleanExtra(EXTRA_FORCE_RELOGIN, false))
    }

    override fun onResume() {
        super.onResume()
        loginWatcherHandler.removeCallbacks(loginWatcher)
        loginWatcherHandler.postDelayed(loginWatcher, LOGIN_WATCH_INTERVAL_MS)
    }

    override fun onPause() {
        loginWatcherHandler.removeCallbacks(loginWatcher)
        super.onPause()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(binding.webViewPortal, true)
            }
            flush()
        }

        binding.webViewPortal.settings.javaScriptEnabled = true
        binding.webViewPortal.settings.domStorageEnabled = true
        binding.webViewPortal.settings.loadsImagesAutomatically = true
        binding.webViewPortal.settings.javaScriptCanOpenWindowsAutomatically = true
        binding.webViewPortal.settings.setSupportMultipleWindows(true)
        binding.webViewPortal.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressLogin.progress = newProgress
                binding.progressLogin.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }

        binding.webViewPortal.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.textLoginStatus.setText(R.string.login_status_loading)
                binding.buttonRetry.visibility = View.GONE
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                inspectPageForLoginSuccess(url ?: PreferencesManager.DEFAULT_BACKEND_URL)
            }
        }
    }

    private fun loadPortal(forceFreshSession: Boolean) {
        if (forceFreshSession) {
            clearWebViewCookies()
            preferencesManager.clearSession()
        }

        completedLogin = false
        binding.webViewPortal.loadUrl(PESU_PORTAL_URL)
    }

    private fun inspectPageForLoginSuccess(url: String) {
        binding.webViewPortal.evaluateJavascript(
            "(function() { return document.body ? document.body.innerText : ''; })();"
        ) { rawValue ->
            if (completedLogin) {
                return@evaluateJavascript
            }

            val pageText = decodeJavascriptString(rawValue)
            val cookie = getBestSessionCookie(url)
            val looksAuthenticated = cookie.isNotBlank() && !htmlParser.isLoginPage(pageText)
            val announcementContextReached = isAnnouncementContext(url, pageText)

            if (looksAuthenticated && announcementContextReached) {
                completedLogin = true
                binding.textLoginStatus.setText(R.string.login_detected)
                preferencesManager.saveBackendCookie(cookie)
                preferencesManager.saveBackendUrl(resolveAnnouncementUrl(url))
                CookieManager.getInstance().flush()
                navigateNext()
            } else if (looksAuthenticated) {
                preferencesManager.saveBackendCookie(cookie)
                binding.textLoginStatus.setText(R.string.login_status_open_announcements)
            } else {
                binding.textLoginStatus.setText(R.string.login_status_waiting)
            }
        }
    }

    private fun navigateNext() {
        Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show()
        if (preferencesManager.isSetupDone()) {
            BackgroundSyncService.start(this)
        }
        val destination = if (preferencesManager.isSetupDone()) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, OnboardingActivity::class.java)
        }

        startActivity(destination)
        finish()
    }

    private fun clearWebViewCookies() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().removeSessionCookies(null)
        CookieManager.getInstance().flush()
    }

    private fun getBestSessionCookie(currentUrl: String): String {
        val cookieManager = CookieManager.getInstance()
        val candidates = linkedSetOf(
            currentUrl,
            PreferencesManager.DEFAULT_BACKEND_URL,
            PESU_PORTAL_URL,
            ACADEMY_ROOT_URL
        )

        URI.create(currentUrl).host?.takeIf { it.isNotBlank() }?.let { host ->
            candidates.add("https://$host/")
        }

        return candidates
            .mapNotNull { candidate -> cookieManager.getCookie(candidate)?.trim() }
            .firstOrNull { cookie ->
                cookie.isNotBlank() && SESSION_COOKIE_NAMES.any { name ->
                    cookie.contains("$name=", ignoreCase = true)
                }
            }
            ?: candidates
                .mapNotNull { candidate -> cookieManager.getCookie(candidate)?.trim() }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
    }

    private fun decodeJavascriptString(rawValue: String): String {
        return runCatching { JSONArray("[$rawValue]").getString(0) }.getOrDefault("")
    }

    private fun resolveAnnouncementUrl(currentUrl: String): String {
        val normalized = currentUrl.trim()
        if (!normalized.startsWith("https://www.pesuacademy.com/", ignoreCase = true)) {
            return PreferencesManager.DEFAULT_BACKEND_URL
        }

        return runCatching {
            val uri = URI(normalized)
            val queryPairs = uri.rawQuery
                ?.split("&")
                ?.mapNotNull { pair ->
                    val index = pair.indexOf('=')
                    if (index <= 0) null else pair.substring(0, index) to pair.substring(index + 1)
                }
                .orEmpty()

            val menuId = queryPairs.firstOrNull { it.first == "menuId" }?.second
            if (menuId.isNullOrBlank()) {
                PreferencesManager.DEFAULT_BACKEND_URL
            } else {
                "https://www.pesuacademy.com/Academy/s/studentProfilePESUAdmin?menuId=$menuId&url=studentProfilePESUAdmin&controllerMode=6411&actionType=5&id=0&selectedData=0"
            }
        }.getOrDefault(PreferencesManager.DEFAULT_BACKEND_URL)
    }

    private fun isAnnouncementContext(currentUrl: String, pageText: String): Boolean {
        val normalizedUrl = currentUrl.lowercase()
        val normalizedText = pageText.lowercase()

        val urlLooksLikeAnnouncementPage =
            normalizedUrl.contains("menuid=") ||
                normalizedUrl.contains("announcement") ||
                normalizedUrl.contains("studentprofilepesuadmin")

        val pageLooksLikeAnnouncementPage =
            normalizedText.contains("announcement") ||
                normalizedText.contains("circular") ||
                normalizedText.contains("notice") ||
                normalizedText.contains("timetable")

        return urlLooksLikeAnnouncementPage || pageLooksLikeAnnouncementPage
    }

    override fun onDestroy() {
        loginWatcherHandler.removeCallbacks(loginWatcher)
        binding.webViewPortal.apply {
            stopLoading()
            destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FORCE_RELOGIN = "force_relogin"
        private const val PESU_PORTAL_URL = "https://www.pesuacademy.com/Academy/"
        private const val ACADEMY_ROOT_URL = "https://www.pesuacademy.com/"
        private const val LOGIN_WATCH_INTERVAL_MS = 1200L
        private val SESSION_COOKIE_NAMES = listOf("JSESSIONID", "PHPSESSID", "AWSALB", "AWSALBCORS")
    }
}

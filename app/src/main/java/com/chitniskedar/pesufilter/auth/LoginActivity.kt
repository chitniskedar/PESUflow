package com.chitniskedar.pesufilter.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
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
import org.json.JSONArray

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var preferencesManager: PreferencesManager
    private val htmlParser = HtmlParser()
    private var completedLogin = false

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

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(binding.webViewPortal, true)
            }
        }

        binding.webViewPortal.settings.javaScriptEnabled = true
        binding.webViewPortal.settings.domStorageEnabled = true
        binding.webViewPortal.settings.loadsImagesAutomatically = true
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
            val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
            val urlChanged = url.removeSuffix("/") != PESU_PORTAL_URL.removeSuffix("/")
            val looksAuthenticated = cookie.isNotBlank() && urlChanged && !htmlParser.isLoginPage(pageText)

            if (looksAuthenticated) {
                completedLogin = true
                preferencesManager.saveBackendCookie(cookie)
                preferencesManager.saveBackendUrl(PreferencesManager.DEFAULT_BACKEND_URL)
                navigateNext()
            } else {
                binding.textLoginStatus.setText(R.string.login_status_waiting)
            }
        }
    }

    private fun navigateNext() {
        Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show()
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
        CookieManager.getInstance().flush()
    }

    private fun decodeJavascriptString(rawValue: String): String {
        return runCatching { JSONArray("[$rawValue]").getString(0) }.getOrDefault("")
    }

    override fun onDestroy() {
        binding.webViewPortal.apply {
            stopLoading()
            destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FORCE_RELOGIN = "force_relogin"
        private const val PESU_PORTAL_URL = "https://www.pesuacademy.com/Academy/"
    }
}

package com.autotube.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private val REQUEST_MEDIA_PROJECTION = 1001

    private val adSkipJs = """
(function(){
  if (window.__adskip) return; window.__adskip = true;
  setInterval(function(){
    var v = document.querySelector('video');
    var ad = document.querySelector('.ad-showing');
    if (ad && v) {
      v.muted = true; v.playbackRate = 16;
      if (isFinite(v.duration)) v.currentTime = v.duration;
    }
    var s = document.querySelector('.ytp-ad-skip-button, .ytp-skip-ad-button, .ytp-ad-skip-button-modern');
    if (s) s.click();
  }, 300);
  var st = document.createElement('style');
  st.textContent = '.ytp-ad-overlay-container, ytd-ad-slot-renderer, .ad-container {display:none !important}';
  document.head.appendChild(st);
})();
"""

    private val blockedHosts = listOf(
        "doubleclick.net", "googlesyndication.com", "/pagead/",
        "/api/stats/ads", "adservice.google"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        webView = findViewById(R.id.webView)

        val btnOpenNative = findViewById<Button>(R.id.btnOpenNativeYoutube)
        val btnReloadWeb = findViewById<Button>(R.id.btnReloadWeb)

        setupWebView()

        btnOpenNative.setOnClickListener {
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
        }

        btnReloadWeb.setOnClickListener {
            webView.loadUrl("https://m.youtube.com")
        }
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (blockedHosts.any { url.contains(it) }) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
                view?.evaluateJavascript(adSkipJs, null)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                view?.evaluateJavascript(adSkipJs, null)
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.loadUrl("https://m.youtube.com")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(this, MirrorService::class.java).apply {
                putExtra("RESULT_CODE", resultCode)
                putExtra("DATA", data)
            }
            startForegroundService(serviceIntent)
            openOriginalYouTube()
        }
    }

    private fun openOriginalYouTube() {
        val youtubePackages = listOf("app.revanced.android.youtube", "com.google.android.youtube")
        for (pkg in youtubePackages) {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Toast.makeText(this, "AutoTube: Orijinal YouTube Başlatıldı!", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(this, "YouTube uygulaması bulunamadı, Web modunda devam ediliyor.", Toast.LENGTH_LONG).show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}

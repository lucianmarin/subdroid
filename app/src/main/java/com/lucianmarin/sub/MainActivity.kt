package com.lucianmarin.sub

import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity(), SwipeRefreshLayout.OnRefreshListener {
    private val myWebView: WebView by lazy {
        findViewById<WebView>(R.id.webView)
    }

    private val mySwipeRefreshLayout: SwipeRefreshLayout by lazy {
        findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        myWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                mySwipeRefreshLayout.isRefreshing = false
            }
        }

        myWebView.settings.javaScriptEnabled = true
        myWebView.loadUrl("https://subreply.com/feed")

        mySwipeRefreshLayout.setOnRefreshListener(this)
    }

    override fun onRefresh() {
        myWebView.loadUrl(myWebView.url)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && myWebView.canGoBack()) {
            myWebView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

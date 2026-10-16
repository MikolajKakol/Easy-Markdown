package io.github.mikolajkakol.easymarkdown

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

private const val ASSET_BASE_URL = "file:///android_asset/mermaid/"
private const val MIN_HEIGHT_DP = 48

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun MermaidDiagram(code: String, darkTheme: Boolean, zoomable: Boolean, modifier: Modifier) {
    var heightDp by remember { mutableIntStateOf(MIN_HEIGHT_DP) }
    val bridge = remember { MermaidWebBridge(zoomable, onHeight = { heightDp = maxOf(it, MIN_HEIGHT_DP) }) }
    bridge.update(code, darkTheme)

    AndroidView(
        modifier = if (zoomable) modifier else modifier.height(heightDp.dp),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isVerticalScrollBarEnabled = zoomable
                isHorizontalScrollBarEnabled = zoomable
                settings.setSupportZoom(zoomable)
                settings.builtInZoomControls = zoomable
                settings.displayZoomControls = false
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                addJavascriptInterface(bridge, "EasyMarkdown")
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        Log.d("EasyMarkdown", "${message.messageLevel()}: ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        bridge.attach(view)
                    }
                }
                // Assets are still readable through file:///android_asset even with allowFileAccess = false.
                loadUrl(ASSET_BASE_URL + "index.html")
            }
        },
        onRelease = { it.destroy() },
    )
}

/** Bridges a WebView page with Compose: pushes diagram source in, gets rendered height back. */
private class MermaidWebBridge(private val zoomable: Boolean, private val onHeight: (Int) -> Unit) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var code: String = ""
    private var darkTheme: Boolean = false
    private var rendered: Pair<String, Boolean>? = null

    fun update(code: String, darkTheme: Boolean) {
        this.code = code
        this.darkTheme = darkTheme
        render()
    }

    fun attach(view: WebView) {
        webView = view
        rendered = null
        render()
    }

    private fun render() {
        val view = webView ?: return
        val request = code to darkTheme
        if (rendered == request) return
        rendered = request
        view.evaluateJavascript("render(${JSONObject.quote(code)}, $darkTheme, $zoomable);", null)
    }

    @JavascriptInterface
    fun onHeight(height: Int) {
        mainHandler.post { onHeight.invoke(height) }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

private val JetBrainsMono = FontFamily(Font(R.font.jetbrains_mono_regular))

@Composable
actual fun codeFontFamily(): FontFamily = JetBrainsMono

package io.github.mikolajkakol.easymarkdown

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

private const val ASSET_BASE_URL = "file:///android_asset/mermaid/"
private const val MIN_HEIGHT_DP = 48

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun MermaidDiagram(code: String, darkTheme: Boolean, modifier: Modifier) {
    var heightDp by remember { mutableIntStateOf(MIN_HEIGHT_DP) }
    val renderer = remember { MermaidRenderer(onHeight = { heightDp = maxOf(it, MIN_HEIGHT_DP) }) }
    renderer.update(code, darkTheme)

    AndroidView(
        modifier = modifier.height(heightDp.dp),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                addJavascriptInterface(renderer, "EasyMarkdown")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        renderer.attach(view)
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
private class MermaidRenderer(private val onHeight: (Int) -> Unit) {
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
        view.evaluateJavascript("render(${JSONObject.quote(code)}, $darkTheme);", null)
    }

    @JavascriptInterface
    fun onHeight(height: Int) {
        mainHandler.post { onHeight.invoke(height) }
    }
}

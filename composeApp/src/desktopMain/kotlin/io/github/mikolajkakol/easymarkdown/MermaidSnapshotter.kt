package io.github.mikolajkakol.easymarkdown

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import javafx.animation.AnimationTimer
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.embed.swing.SwingFXUtils
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.SnapshotParameters
import javafx.scene.paint.Color
import javafx.scene.web.WebView
import javax.swing.JWindow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import netscape.javascript.JSObject

/** A diagram rasterized at a few pixels per CSS px; [width] and [height] are in CSS px, drawn as dp. */
class RenderedDiagram(val image: ImageBitmap, val width: Int, val height: Int)

/**
 * Renders mermaid diagrams to images with a JavaFX WebView, as Compose Desktop has no web view of its own.
 * A single page, never shown on screen, renders the diagrams one at a time.
 */
object MermaidSnapshotter {
    private const val RENDER_TIMEOUT_MS = 30_000L
    private const val CACHE_SIZE = 16

    private data class Request(val code: String, val darkTheme: Boolean, val maxWidth: Int, val scale: Float)

    private val mutex = Mutex()
    private var page: MermaidPage? = null
    private val cache = object : LinkedHashMap<Request, RenderedDiagram>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Request, RenderedDiagram>) = size > CACHE_SIZE
    }

    /**
     * Renders [code] at most [maxWidth] CSS px wide with [scale] pixels per CSS px.
     * Returns null if the page could not render it; mermaid syntax errors are rendered as text.
     */
    suspend fun render(code: String, darkTheme: Boolean, maxWidth: Int, scale: Float): RenderedDiagram? {
        val request = Request(code, darkTheme, maxWidth, scale)
        synchronized(cache) { cache[request] }?.let { return it }
        // A started render always finishes, otherwise its result would be reported to the next one.
        return withContext(NonCancellable) {
            mutex.withLock {
                synchronized(cache) { cache[request] }?.let { return@withLock it }
                try {
                    val page = page ?: MermaidPage.load().also { page = it }
                    withTimeout(RENDER_TIMEOUT_MS) { page.render(request.code, darkTheme, maxWidth, scale) }
                        ?.also { synchronized(cache) { cache[request] = it } }
                } catch (e: Exception) {
                    System.err.println("Mermaid rendering failed: $e")
                    page = null
                    null
                }
            }
        }
    }
}

private class MermaidPage(private val view: WebView) {
    private var pending: CompletableDeferred<Pair<Int, Int>>? = null

    // JavaFX keeps only a weak reference to objects passed to JavaScript, so the page holds it.
    private val bridge = MermaidBridge { width, height -> pending?.complete(width to height) }

    suspend fun render(code: String, darkTheme: Boolean, maxWidth: Int, scale: Float): RenderedDiagram? =
        withContext(FxDispatcher) {
            val rendered = CompletableDeferred<Pair<Int, Int>>().also { pending = it }
            view.zoom = 1.0
            // Long error messages wrap at the page width; diagrams are sized by the script.
            view.setSize(min(maxWidth, 1600).toDouble(), 100.0)
            window().call("render", code, darkTheme, maxWidth)
            val (width, height) = rendered.await()
            if (width <= 0 || height <= 0) return@withContext null

            val pixelScale = min(scale.toDouble(), MAX_PIXELS_PER_SIDE / max(width, height))
            view.zoom = pixelScale
            view.setSize(width * pixelScale, height * pixelScale)
            awaitPulses(3)
            val snapshot = view.snapshot(SnapshotParameters().apply { fill = Color.TRANSPARENT }, null)
            RenderedDiagram(SwingFXUtils.fromFXImage(snapshot, null).toComposeImageBitmap(), width, height)
        }

    private fun window() = view.engine.executeScript("window") as JSObject

    companion object {
        /** Largest side of a rendered image in pixels; bigger diagrams get fewer pixels per CSS px. */
        private const val MAX_PIXELS_PER_SIDE = 4096.0

        suspend fun load(): MermaidPage {
            // Creating a JFXPanel starts JavaFX alongside AWT. Its window is packed but never shown: that is
            // enough for JavaFX to paint the scene, without a window that could take focus.
            val panel = withContext(Dispatchers.Swing) {
                JFXPanel().also { JWindow().apply { add(it); pack() } }
            }
            Platform.setImplicitExit(false)
            return withContext(FxDispatcher) {
                val view = WebView().apply {
                    isContextMenuEnabled = false
                    pageFill = Color.TRANSPARENT
                }
                panel.scene = Scene(Group(view), Color.TRANSPARENT)
                val url = MermaidPage::class.java.getResource("/mermaid/desktop.html")
                    ?: error("Missing mermaid page resource")
                view.engine.awaitLoad(url.toExternalForm())
                MermaidPage(view).also { it.window().setMember("EasyMarkdown", it.bridge) }
            }
        }
    }
}

/** Called by the page's script; must be public for JavaFX to see it. */
class MermaidBridge internal constructor(private val onRendered: (width: Int, height: Int) -> Unit) {
    fun onRendered(width: Int, height: Int) = onRendered.invoke(width, height)
}

private object FxDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = Platform.runLater(block)
}

private fun WebView.setSize(width: Double, height: Double) {
    setMinSize(width, height)
    setPrefSize(width, height)
    setMaxSize(width, height)
}

private suspend fun javafx.scene.web.WebEngine.awaitLoad(url: String) = suspendCancellableCoroutine { cont ->
    loadWorker.stateProperty().addListener { _, _, state ->
        when (state) {
            Worker.State.SUCCEEDED -> if (cont.isActive) cont.resume(Unit)
            Worker.State.FAILED -> if (cont.isActive) cont.resumeWithException(IllegalStateException("Cannot load $url"))
            else -> Unit
        }
    }
    load(url)
}

/** Waits for JavaFX to lay out and paint the page after it was resized. */
private suspend fun awaitPulses(count: Int) = suspendCancellableCoroutine { cont ->
    object : AnimationTimer() {
        var frames = 0
        override fun handle(now: Long) {
            if (++frames < count) return
            stop()
            cont.resume(Unit)
        }
    }.start()
}

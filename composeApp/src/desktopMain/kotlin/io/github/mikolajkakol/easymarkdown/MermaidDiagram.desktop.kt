package io.github.mikolajkakol.easymarkdown

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val MinHeight = 48.dp

/** Full screen diagrams are rendered at their natural size with this many pixels per CSS px, for zooming in. */
private const val FULL_SCREEN_SCALE = 4f
private const val NATURAL_WIDTH = 100_000

private val isMac = System.getProperty("os.name").startsWith("Mac")

@Composable
actual fun MermaidDiagram(code: String, darkTheme: Boolean, zoomable: Boolean, modifier: Modifier) {
    if (zoomable) ZoomableDiagram(code, darkTheme, modifier) else InlineDiagram(code, darkTheme, modifier)
}

@Composable
private fun InlineDiagram(code: String, darkTheme: Boolean, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val maxWidth = if (constraints.hasBoundedWidth) maxWidth.value.toInt() else 800
        val diagram = rememberDiagram(code, darkTheme, maxWidth, LocalDensity.current.density)
        if (diagram == null) {
            Spacer(Modifier.fillMaxWidth().height(MinHeight))
        } else {
            Image(
                bitmap = diagram.image,
                contentDescription = null,
                filterQuality = FilterQuality.High,
                modifier = Modifier.size(diagram.width.dp, diagram.height.dp),
            )
        }
    }
}

@Composable
private fun ZoomableDiagram(code: String, darkTheme: Boolean, modifier: Modifier) {
    val diagram = rememberDiagram(code, darkTheme, NATURAL_WIDTH, FULL_SCREEN_SCALE)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (diagram == null) {
            CircularProgressIndicator()
        } else {
            ZoomableImage(diagram, Modifier.fillMaxSize())
            Text(
                text = "Drag or scroll to move · ${if (isMac) "⌘" else "Ctrl"} + scroll to zoom · double-click to fit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** Renders the diagram, keeping the previous image while the window is resized or the theme changes. */
@Composable
private fun rememberDiagram(code: String, darkTheme: Boolean, maxWidth: Int, scale: Float): RenderedDiagram? {
    var diagram by remember(code) { mutableStateOf<RenderedDiagram?>(null) }
    LaunchedEffect(code, darkTheme, maxWidth, scale) {
        // Waits for the width to settle before re-rendering during a window resize.
        if (diagram != null) delay(200)
        MermaidSnapshotter.render(code, darkTheme, maxWidth, scale)?.let { diagram = it }
    }
    return diagram
}

/** Where the diagram is drawn: its top-left corner and pixels per CSS px. */
private data class DiagramTransform(val offset: Offset, val scale: Float) {
    fun pan(delta: Offset) = copy(offset = offset + delta)

    /** Zooms by [factor] keeping the point under [focus] in place. */
    fun zoom(factor: Float, focus: Offset, density: Float): DiagramTransform {
        val newScale = (scale * factor).coerceIn(0.1f * density, 12f * density)
        return DiagramTransform(focus - (focus - offset) * (newScale / scale), newScale)
    }

    companion object {
        /** Fits the diagram in [size] with a margin, enlarging small diagrams at most twice. */
        fun fit(diagram: RenderedDiagram, size: IntSize, density: Float): DiagramTransform {
            val margin = 16 * density
            val scale = minOf(
                (size.width - 2 * margin) / diagram.width,
                (size.height - 2 * margin) / diagram.height,
                2 * density,
            ).coerceAtLeast(0.01f)
            val offset = Offset((size.width - diagram.width * scale) / 2, (size.height - diagram.height * scale) / 2)
            return DiagramTransform(offset, scale)
        }
    }
}

/** Shows the diagram fitted to the available space, with zooming and panning by mouse or trackpad. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ZoomableImage(diagram: RenderedDiagram, modifier: Modifier) {
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Null while the diagram is fitted to the window, so it stays fitted when the window is resized.
    var transform by remember(diagram) { mutableStateOf<DiagramTransform?>(null) }
    fun current() = transform ?: DiagramTransform.fit(diagram, size, density)

    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(diagram) {
                detectDragGestures { change, drag ->
                    change.consume()
                    transform = current().pan(drag)
                }
            }
            .pointerInput(diagram) { detectTapGestures(onDoubleTap = { transform = null }) }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.first()
                val delta = change.scrollDelta
                val modifiers = event.keyboardModifiers
                transform = if (modifiers.isMetaPressed || modifiers.isCtrlPressed) {
                    current().zoom(1.1f.pow(-delta.y), change.position, density)
                } else {
                    current().pan(-delta * (40 * density))
                }
                change.consume()
            },
    ) {
        val t = current()
        drawImage(
            image = diagram.image,
            dstOffset = IntOffset(t.offset.x.roundToInt(), t.offset.y.roundToInt()),
            dstSize = IntSize((diagram.width * t.scale).roundToInt(), (diagram.height * t.scale).roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }
}

/** Handlers of the Escape key, which closes full screen views like the back gesture on Android. */
class BackHandlers {
    private val handlers = mutableListOf<() -> Unit>()

    fun add(handler: () -> Unit) = handlers.add(handler)
    fun remove(handler: () -> Unit) = handlers.remove(handler)

    /** Runs the most recently added handler; returns false if there is none. */
    fun onBack(): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        handler()
        return true
    }
}

val LocalBackHandlers = staticCompositionLocalOf { BackHandlers() }

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val handlers = LocalBackHandlers.current
    val currentOnBack by rememberUpdatedState(onBack)
    if (enabled) {
        DisposableEffect(handlers) {
            val handler = { currentOnBack() }
            handlers.add(handler)
            onDispose { handlers.remove(handler) }
        }
    }
}

private val JetBrainsMono by lazy {
    val bytes = RenderedDiagram::class.java.getResourceAsStream("/jetbrains_mono_regular.ttf")!!.use { it.readBytes() }
    FontFamily(Font("JetBrainsMono-Regular", bytes))
}

@Composable
actual fun codeFontFamily(): FontFamily = JetBrainsMono

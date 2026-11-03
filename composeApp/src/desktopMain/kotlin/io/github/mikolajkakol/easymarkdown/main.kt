package io.github.mikolajkakol.easymarkdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

private val isMac = System.getProperty("os.name").startsWith("Mac")

fun main(args: Array<String>) {
    val documents = OpenDocuments()
    // Finder and `open file.md` pass files as Apple Events rather than arguments, also to a running app.
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE)) {
        Desktop.getDesktop().setOpenFileHandler { event ->
            SwingUtilities.invokeLater { event.files.forEach(documents::open) }
        }
    }
    args.map(::File).filter { it.isFile }.forEach(documents::open)
    documents.openSampleIfEmpty()
    thread(isDaemon = true) { DefaultMarkdownApp.claimOnFirstLaunch() }

    // The app quits when its last window is closed.
    application {
        for (window in documents.windows) {
            key(window) { DocumentWindow(window, documents) }
        }
    }
}

/** The app's windows, one per document. */
private class OpenDocuments {
    val windows = mutableStateListOf<DocumentWindowState>()

    /** Shows [file] in its window if already open, else in the untouched sample window or a new one. */
    @Synchronized
    fun open(file: File) {
        val path = file.absoluteFile
        val existing = windows.firstOrNull { it.file == path }
        val sample = windows.singleOrNull()?.takeIf { it.file == null }
        when {
            existing != null -> existing.reload()
            sample != null -> sample.file = path
            else -> windows += DocumentWindowState(path)
        }
    }

    @Synchronized
    fun openSampleIfEmpty() {
        if (windows.isEmpty()) windows += DocumentWindowState(file = null)
    }

    fun close(window: DocumentWindowState) {
        windows -= window
    }
}

private class DocumentWindowState(file: File?) {
    /** The shown file, or null for the sample document. */
    var file by mutableStateOf(file)

    /** Incremented to read the file again and bring its window to the front. */
    var reloads by mutableIntStateOf(0)

    /** Files the shown one was opened from with a link, the most recent last. */
    val backStack = mutableStateListOf<File>()

    fun reload() {
        reloads++
    }

    /** Shows the markdown file [link] points to in this window; returns false for other links. */
    fun openLink(link: LocalLink): Boolean {
        val current = file ?: return false
        if (!link.isMarkdown) return false
        val target = File(resolvePath(current.absoluteFile.parentFile.invariantSeparatorsPath, link))
        if (target == current) return true
        backStack += current
        file = target
        return true
    }

    fun back() {
        backStack.removeLastOrNull()?.let { file = it }
    }
}

@Composable
private fun DocumentWindow(state: DocumentWindowState, documents: OpenDocuments) {
    var document by remember { mutableStateOf(if (state.file == null) SampleDocument else OpenedDocument("Loading…", "")) }
    val backHandlers = remember { BackHandlers() }
    val search = rememberSearchState()

    Window(
        onCloseRequest = { documents.close(state) },
        state = rememberWindowState(size = DpSize(820.dp, 900.dp)),
        title = document.title,
        onPreviewKeyEvent = { it.type == KeyEventType.KeyDown && it.key == Key.Escape && backHandlers.onBack() },
    ) {
        LaunchedEffect(Unit) { window.minimumSize = Dimension(400, 300) }
        LaunchedEffect(state.file, state.reloads) {
            val file = state.file ?: return@LaunchedEffect
            // Shows the file's icon in the macOS title bar.
            window.rootPane.putClientProperty("Window.documentFile", file)
            document = withContext(Dispatchers.IO) { readDocument(file) }
            window.toFront()
        }
        // Shows the file's changes as it is edited elsewhere, without bringing the window to the front.
        LaunchedEffect(state.file) {
            val file = state.file ?: return@LaunchedEffect
            fileChanges(file).collectLatest {
                // Skips the moment between an editor deleting the file and writing it again.
                if (file.isFile) document = withContext(Dispatchers.IO) { readDocument(file) }
            }
        }
        val openFile: () -> Unit = { chooseMarkdownFile(window)?.let(documents::open) }
        val canGoBack = state.backStack.isNotEmpty()

        MenuBar {
            Menu("File") {
                Item("Open…", shortcut = shortcut(Key.O), onClick = openFile)
                Item("Reload", enabled = state.file != null, shortcut = shortcut(Key.R), onClick = state::reload)
                Item("Close Window", shortcut = shortcut(Key.W), onClick = { documents.close(state) })
                Separator()
                Item("Back", enabled = canGoBack, shortcut = shortcut(Key.LeftBracket), onClick = state::back)
                if (DefaultMarkdownApp.isSupported) {
                    Separator()
                    Item("Make Default App for Markdown Files") {
                        val message = if (runCatching { DefaultMarkdownApp.makeDefault() }.getOrDefault(false)) {
                            "Easy Markdown now opens markdown files."
                        } else {
                            "Could not make Easy Markdown the default app. Use Get Info → Open with in Finder instead."
                        }
                        JOptionPane.showMessageDialog(window, message, "Easy Markdown", JOptionPane.INFORMATION_MESSAGE)
                    }
                }
            }
            Menu("Edit") {
                Item("Find…", shortcut = shortcut(Key.F), onClick = search::open)
                Item("Find Next", shortcut = shortcut(Key.G), onClick = search::next)
                Item("Find Previous", shortcut = shortcut(Key.G, shift = true), onClick = search::previous)
            }
        }

        CompositionLocalProvider(LocalBackHandlers provides backHandlers) {
            App(
                document = document,
                onOpenFile = openFile,
                search = search,
                onOpenLink = { link -> state.openLink(link) || openWithSystem(state.file, link) },
                onBack = if (canGoBack) state::back else null,
            )
        }
    }
}

private fun shortcut(key: Key, shift: Boolean = false) = KeyShortcut(key, meta = isMac, ctrl = !isMac, shift = shift)

private fun readDocument(file: File): OpenedDocument = try {
    OpenedDocument(title = file.name, content = file.readText(), location = file.path)
} catch (e: Exception) {
    OpenedDocument(title = "Error", content = "# Cannot open file\n\n`${e.message}`", location = file.path)
}

/** Opens a linked file that is not markdown, e.g. an image or PDF, in its default app. */
private fun openWithSystem(document: File?, link: LocalLink): Boolean {
    val base = document?.absoluteFile?.parentFile ?: return false
    val target = File(resolvePath(base.invariantSeparatorsPath, link))
    if (!target.exists() || !Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) return false
    thread(isDaemon = true) { runCatching { Desktop.getDesktop().open(target) } }
    return true
}

private fun chooseMarkdownFile(parent: Frame): File? {
    val dialog = FileDialog(parent, "Open Markdown File", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in markdownExtensions }
    dialog.isVisible = true
    return dialog.file?.let { File(dialog.directory, it) }
}

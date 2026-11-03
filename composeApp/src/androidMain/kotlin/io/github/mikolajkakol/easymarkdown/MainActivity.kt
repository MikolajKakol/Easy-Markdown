package io.github.mikolajkakol.easymarkdown

import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.io.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /** Incremented to read the opened file again, e.g. after it was edited in another app. */
    private var fileChanges by mutableIntStateOf(0)
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val launchSource = intent?.let(::documentSource)

        setContent {
            // Only the source (URI or shared text) is saved; file contents are re-read after recreation.
            var source by rememberSaveable { mutableStateOf(launchSource) }
            // Documents the shown one was opened from with a link, the most recent last.
            var backStack by rememberSaveable { mutableStateOf(listOf<DocumentSource>()) }
            var document by remember {
                mutableStateOf(if (source == null) SampleDocument else OpenedDocument("Loading…", ""))
            }

            LaunchedEffect(source) {
                when (val current = source) {
                    null -> Unit
                    is DocumentSource.Text -> document = OpenedDocument(current.title, current.markdown)
                    is DocumentSource.File -> document = withContext(Dispatchers.IO) { readDocument(Uri.parse(current.uri)) }
                }
            }
            // Re-reads the file when its provider reports a change or the app comes back to the foreground.
            val file = source as? DocumentSource.File
            if (file != null) {
                DisposableEffect(file) {
                    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            fileChanges++
                        }
                    }
                    // Not every provider supports observers; returning to the app still reloads the file.
                    val registered = runCatching {
                        contentResolver.registerContentObserver(Uri.parse(file.uri), false, observer)
                    }.isSuccess
                    onDispose { if (registered) contentResolver.unregisterContentObserver(observer) }
                }
                LaunchedEffect(file, fileChanges) {
                    if (fileChanges == 0) return@LaunchedEffect
                    val reloaded = withContext(Dispatchers.IO) { readDocument(Uri.parse(file.uri)) }
                    // Keeps the last content when the file is briefly unreadable, e.g. while being saved.
                    if (reloaded.title != "Error") document = reloaded
                }
            }

            val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    source = DocumentSource.File(uri.toString())
                    backStack = emptyList()
                }
            }
            val openLink = openLink@{ link: LocalLink ->
                val current = source as? DocumentSource.File ?: return@openLink false
                if (!link.isMarkdown) return@openLink false
                val target = resolveLink(Uri.parse(current.uri), link) ?: return@openLink false
                backStack = backStack + current
                source = DocumentSource.File(target.toString())
                true
            }

            App(
                document = document,
                onOpenFile = { openFile.launch(arrayOf("text/markdown", "text/x-markdown", "text/plain", "application/octet-stream")) },
                onOpenLink = openLink,
                onBack = if (backStack.isEmpty()) null else {
                    {
                        source = backStack.last()
                        backStack = backStack.dropLast(1)
                    }
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        if (started) fileChanges++
        started = true
    }

    /** What to show for an "Open with" or share intent; null for a plain launch. */
    private fun documentSource(intent: Intent): DocumentSource? = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data?.let { DocumentSource.File(it.toString()) }
        Intent.ACTION_SEND -> intent.streamUri()?.let { DocumentSource.File(it.toString()) }
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { text ->
                val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() } ?: "Shared text"
                DocumentSource.Text(title, text.toString())
            }
        else -> null
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else getParcelableExtra(Intent.EXTRA_STREAM)

    private fun readDocument(uri: Uri): OpenedDocument = try {
        val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Cannot open $uri")
        OpenedDocument(title = displayName(uri) ?: "Document", content = text, location = uri.toString())
    } catch (e: Exception) {
        OpenedDocument(title = "Error", content = "# Cannot open file\n\n`${e.message}`", location = uri.toString())
    }

    /**
     * The URI of the file [link] points to, relative to the document at [base]. Storage Access Framework
     * documents name their folder in the document ID, e.g. `primary:Notes/a.md`; reading a sibling found
     * that way can still fail when the app was only granted access to the opened file.
     */
    private fun resolveLink(base: Uri, link: LocalLink): Uri? {
        if (base.scheme == "file") {
            val directory = base.path?.substringBeforeLast('/', "") ?: return null
            return Uri.fromFile(java.io.File(resolvePath(directory, link)))
        }
        if (base.scheme != "content") return null
        if (DocumentsContract.isDocumentUri(this, base)) {
            val id = DocumentsContract.getDocumentId(base)
            // The ID's root, like `primary:` or `raw:`, and the path within it.
            val root = if (':' in id) id.substringBefore(':') + ":" else ""
            val path = id.removePrefix(root)
            if (root.isEmpty() && '/' !in path) return null
            val directory = "/" + path.removePrefix("/").substringBeforeLast('/', "")
            val newPath = resolvePath(directory, link).let { if (path.startsWith("/")) it else it.removePrefix("/") }
            return if (DocumentsContract.isTreeUri(base)) {
                DocumentsContract.buildDocumentUriUsingTree(base, root + newPath)
            } else {
                DocumentsContract.buildDocumentUri(base.authority, root + newPath)
            }
        }
        // Providers with plain paths, like `content://provider/files/notes/a.md`.
        val segments = base.pathSegments.dropLast(1)
        val resolved = resolvePath("/" + segments.joinToString("/"), link)
        return base.buildUpon().path(resolved).build()
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment
}

private sealed interface DocumentSource : Serializable {
    /** A file opened from a content/file URI. */
    data class File(val uri: String) : DocumentSource

    /** Markdown shared directly as text. */
    data class Text(val title: String, val markdown: String) : DocumentSource
}

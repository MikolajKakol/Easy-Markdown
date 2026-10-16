package io.github.mikolajkakol.easymarkdown

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.io.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val launchSource = intent?.let(::documentSource)

        setContent {
            // Only the source (URI or shared text) is saved; file contents are re-read after recreation.
            var source by rememberSaveable { mutableStateOf(launchSource) }
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

            val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) source = DocumentSource.File(uri.toString())
            }

            App(
                document = document,
                onOpenFile = { openFile.launch(arrayOf("text/markdown", "text/x-markdown", "text/plain", "application/octet-stream")) },
                onShowSample = {
                    source = null
                    document = SampleDocument
                },
            )
        }
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
        OpenedDocument(title = displayName(uri) ?: "Document", content = text)
    } catch (e: Exception) {
        OpenedDocument(title = "Error", content = "# Cannot open file\n\n`${e.message}`")
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

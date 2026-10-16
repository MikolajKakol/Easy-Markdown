package io.github.mikolajkakol.easymarkdown

import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val launchUri = intent?.data?.toString()

        setContent {
            var documentUri by rememberSaveable { mutableStateOf(launchUri) }
            var document by remember {
                mutableStateOf(if (documentUri == null) SampleDocument else OpenedDocument("Loading…", ""))
            }

            LaunchedEffect(documentUri) {
                val uri = documentUri ?: return@LaunchedEffect
                document = withContext(Dispatchers.IO) { readDocument(Uri.parse(uri)) }
            }

            val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) documentUri = uri.toString()
            }

            App(
                document = document,
                onOpenFile = { openFile.launch(arrayOf("text/markdown", "text/x-markdown", "text/plain", "application/octet-stream")) },
                onShowSample = {
                    documentUri = null
                    document = SampleDocument
                },
            )
        }
    }

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

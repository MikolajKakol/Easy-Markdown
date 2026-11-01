package io.github.mikolajkakol.easymarkdown

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class FileChangesTest {
    private val dir = Files.createTempDirectory("file-changes").toFile().apply { deleteOnExit() }

    @Test
    fun emitsWhenFileIsWritten() = runBlocking {
        val file = File(dir, "doc.md").apply { writeText("# One") }
        val change = async { withTimeout(5_000) { fileChanges(file, interval = 50).first() } }
        delay(300)
        file.writeText("# Two, longer")
        change.await()
    }

    @Test
    fun emitsWhenFileIsReplacedAtomically() = runBlocking {
        val file = File(dir, "atomic.md").apply { writeText("# One") }
        val change = async { withTimeout(5_000) { fileChanges(file, interval = 50).first() } }
        delay(300)
        val temp = File(dir, "atomic.md.tmp").apply { writeText("# Two, longer") }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        change.await()
    }
}

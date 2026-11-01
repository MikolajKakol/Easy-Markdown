package io.github.mikolajkakol.easymarkdown

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Emits whenever [file] changes on disk: it is saved, replaced (editors often save by writing a new file
 * and renaming it over the old one), deleted or created again. Nothing is emitted for the initial state.
 *
 * The file is polled instead of using `WatchService`, which on macOS is itself a 10 s poll, and misses
 * atomic saves unless the whole directory is watched. A stat call every [interval] costs next to nothing.
 */
fun fileChanges(file: File, interval: Long = 500): Flow<Unit> = flow {
    while (true) {
        emit(FileStamp.of(file))
        delay(interval)
    }
}
    .distinctUntilChanged()
    .drop(1)
    .map { }
    .flowOn(Dispatchers.IO)

private data class FileStamp(val exists: Boolean, val modified: Long, val length: Long) {
    companion object {
        fun of(file: File) = FileStamp(file.exists(), file.lastModified(), file.length())
    }
}

package io.github.mikolajkakol.easymarkdown

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File
import java.util.prefs.Preferences

/**
 * Makes the macOS app the default for markdown files (Finder, `open file.md`) through Launch Services.
 * The app's Info.plist declares it as a viewer of the system markdown type, `net.daringfireball.markdown`.
 */
object DefaultMarkdownApp {
    private const val BUNDLE_ID = "io.github.mikolajkakol.easymarkdown"
    private const val MARKDOWN_TYPE = "net.daringfireball.markdown"
    private const val ROLES_ALL = -1 // kLSRolesAll
    private const val UTF8 = 0x08000100 // kCFStringEncodingUTF8
    private const val CLAIMED_KEY = "claimedMarkdownDefault"

    /** The running .app bundle; null when not run from one, e.g. with `./gradlew run`. */
    private val bundle: File? = System.getProperty("jpackage.app-path")
        ?.takeIf { System.getProperty("os.name").startsWith("Mac") }
        ?.let { path -> generateSequence(File(path)) { it.parentFile }.firstOrNull { it.name.endsWith(".app") } }

    val isSupported: Boolean get() = bundle != null

    fun isDefault(): Boolean = withCFString(MARKDOWN_TYPE) { type ->
        val handler = launchServices.LSCopyDefaultRoleHandlerForContentType(type, ROLES_ALL) ?: return@withCFString false
        try {
            toKotlinString(handler).equals(BUNDLE_ID, ignoreCase = true)
        } finally {
            coreFoundation.CFRelease(handler)
        }
    }

    /** Returns true if this app is now the default for markdown files. */
    fun makeDefault(): Boolean {
        val bundle = bundle ?: return false
        // Registers this copy of the app, so Launch Services knows its document types even if not launched from Finder.
        val path = bundle.absolutePath.toByteArray()
        coreFoundation.CFURLCreateFromFileSystemRepresentation(null, path, path.size.toLong(), 1)?.let { url ->
            launchServices.LSRegisterURL(url, 1)
            coreFoundation.CFRelease(url)
        }
        val status = withCFString(MARKDOWN_TYPE) { type ->
            withCFString(BUNDLE_ID) { id -> launchServices.LSSetDefaultRoleHandlerForContentType(type, ROLES_ALL, id) }
        }
        return status == 0 && isDefault()
    }

    /**
     * Makes the app the default on its first launch. Done once, so choosing another app later in Finder sticks;
     * the File menu can make it the default again.
     */
    fun claimOnFirstLaunch() {
        if (!isSupported) return
        val prefs = Preferences.userNodeForPackage(DefaultMarkdownApp::class.java)
        if (prefs.getBoolean(CLAIMED_KEY, false)) return
        runCatching {
            if (isDefault() || makeDefault()) prefs.putBoolean(CLAIMED_KEY, true)
        }.onFailure { System.err.println("Cannot set default markdown app: $it") }
    }

    private fun <T> withCFString(value: String, block: (Pointer) -> T): T {
        val string = coreFoundation.CFStringCreateWithCString(null, value, UTF8) ?: error("Cannot create CFString")
        try {
            return block(string)
        } finally {
            coreFoundation.CFRelease(string)
        }
    }

    private fun toKotlinString(string: Pointer): String {
        val buffer = ByteArray(1024)
        if (coreFoundation.CFStringGetCString(string, buffer, buffer.size.toLong(), UTF8).toInt() == 0) return ""
        return String(buffer, 0, buffer.indexOf(0).coerceAtLeast(0))
    }

    private val coreFoundation by lazy {
        Native.load("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", CoreFoundation::class.java)
    }
    private val launchServices by lazy {
        Native.load("/System/Library/Frameworks/CoreServices.framework/CoreServices", LaunchServices::class.java)
    }
}

// C Boolean is an unsigned char, hence Byte.
@Suppress("FunctionName")
internal interface CoreFoundation : Library {
    fun CFStringCreateWithCString(allocator: Pointer?, string: String, encoding: Int): Pointer?
    fun CFStringGetCString(string: Pointer, buffer: ByteArray, bufferSize: Long, encoding: Int): Byte
    fun CFURLCreateFromFileSystemRepresentation(allocator: Pointer?, path: ByteArray, length: Long, isDirectory: Byte): Pointer?
    fun CFRelease(ref: Pointer)
}

@Suppress("FunctionName")
internal interface LaunchServices : Library {
    fun LSCopyDefaultRoleHandlerForContentType(contentType: Pointer, role: Int): Pointer?
    fun LSSetDefaultRoleHandlerForContentType(contentType: Pointer, role: Int, handlerBundleId: Pointer): Int
    fun LSRegisterURL(url: Pointer, update: Byte): Int
}

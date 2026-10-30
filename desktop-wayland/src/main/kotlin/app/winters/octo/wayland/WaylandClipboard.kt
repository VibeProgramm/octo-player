@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@file:Suppress("DEPRECATION")

package app.winters.octo.wayland

import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.text.AnnotatedString
import app.winters.octo.desktop.system.TextClipboard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.desktop.linux.Application
import org.jetbrains.desktop.linux.DataSource
import org.jetbrains.desktop.linux.Event
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

internal val TextMimes = listOf("text/plain;charset=utf-8", "text/plain", "UTF8_STRING")

internal class WaylandClipboard(private val application: Application) : TextClipboard, Clipboard {
    private var cached: String? = null
    private var owned: String? = null
    private var serial = 0
    private var receivedSerial = 0
    private val requests = mutableMapOf<Int, CompletableDeferred<String?>>()

    override fun text(): String? = cached
    override fun put(text: String) {
        owned = text
        cached = text
        application.clipboardPut(TextMimes)
    }

    override suspend fun read(): String? {
        owned?.let { return it }
        if (application.clipboardGetAvailableMimeTypes().none { it in TextMimes }) {
            cached = null
            return null
        }
        val id = ++serial
        val reply = CompletableDeferred<String?>()
        requests[id] = reply
        try {
            application.clipboardPaste(id, TextMimes)
            return withTimeoutOrNull(3_000) { reply.await() }
        } finally {
            requests.remove(id)
        }
    }

    fun receive(event: Event.DataTransfer) {
        val reply = requests.remove(event.serial) ?: return
        val text = event.content?.data?.decodeToString()
        if (event.serial >= receivedSerial) {
            receivedSerial = event.serial
            cached = text
        }
        reply.complete(text)
    }

    fun cancelled() { owned = null; cached = null }
    fun data(source: DataSource, mime: String): ByteArray? =
        if (source == DataSource.Clipboard && mime in TextMimes) owned?.toByteArray() else null

    // Transferable is a data container here; it never opens an AWT clipboard or window.
    override suspend fun getClipEntry(): ClipEntry? = read()?.let { ClipEntry(StringSelection(it)) }
    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val text = clipEntry?.asAwtTransferable?.let {
            if (it.isDataFlavorSupported(DataFlavor.stringFlavor)) it.getTransferData(DataFlavor.stringFlavor) as? String else null
        }
        put(text.orEmpty())
    }
    override val nativeClipboard: Any get() = this

    val legacy = object : ClipboardManager {
        override fun getText(): AnnotatedString? = cached?.let(::AnnotatedString)
        override fun setText(annotatedString: AnnotatedString) = put(annotatedString.text)
        override fun hasText(): Boolean = !cached.isNullOrEmpty()
    }
}

@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@file:Suppress("DEPRECATION")

package app.winters.octo.wayland

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.desktop.linux.Application
import org.jetbrains.desktop.linux.Event
import org.jetbrains.desktop.linux.LogicalRect
import org.jetbrains.desktop.linux.TextInputContentHint
import org.jetbrains.desktop.linux.TextInputContentPurpose
import org.jetbrains.desktop.linux.TextInputContext
import kotlin.math.roundToInt

// Compose's selection/edit processor continues to handle arrows, word movement,
// undo, selection and shortcuts. Only XKB text and text-input-v3 edits enter here.
internal class WaylandTextInput(
    private val application: Application,
    private val scale: () -> Float,
) : PlatformTextInputService {
    private var request: PlatformTextInputMethodRequest? = null
    private var legacyValue = TextFieldValue()
    private var legacyOptions = ImeOptions.Default
    private var legacyEdit: ((List<EditCommand>) -> Unit)? = null
    private var legacyAction: ((ImeAction) -> Unit)? = null
    private var legacyRect = Rect.Zero
    private var enabled = false
    private var lastContext: TextInputContext? = null
    private var fromIme = false
    var available = false
        set(value) { field = value; sync() }
    var focused = false
        set(value) { field = value; sync() }

    val active: Boolean get() = request != null || legacyEdit != null
    private val value: TextFieldValue get() = request?.value?.invoke() ?: legacyValue
    private val options: ImeOptions get() = request?.imeOptions ?: legacyOptions

    suspend fun start(request: PlatformTextInputMethodRequest): Nothing = coroutineScope {
        this@WaylandTextInput.request = request
        try {
            launch { snapshotFlow { request.value() to request.focusedRectInRoot() }.collect { sync() } }
            sync()
            awaitCancellation()
        } finally {
            if (this@WaylandTextInput.request === request) {
                this@WaylandTextInput.request = null
                sync()
            }
        }
    }

    override fun startInput(value: TextFieldValue, imeOptions: ImeOptions, onEditCommand: (List<EditCommand>) -> Unit, onImeActionPerformed: (ImeAction) -> Unit) {
        legacyValue = value
        legacyOptions = imeOptions
        legacyEdit = onEditCommand
        legacyAction = onImeActionPerformed
        sync()
    }
    override fun stopInput() { legacyEdit = null; legacyAction = null; sync() }
    override fun showSoftwareKeyboard() = sync()
    override fun hideSoftwareKeyboard() = Unit
    override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) { legacyValue = newValue; sync() }
    override fun notifyFocusedRect(rect: Rect) { legacyRect = rect; sync() }

    private fun edit(commands: List<EditCommand>) {
        (request?.onEditCommand ?: legacyEdit)?.invoke(commands)
    }

    fun type(text: String) {
        if (active && text.isNotEmpty()) edit(listOf(CommitTextCommand(text, 1)))
    }

    fun sync() {
        if (!available || !focused || !active) {
            if (enabled) application.textInputDisable()
            enabled = false
            lastContext = null
            return
        }
        val password = options.keyboardType == KeyboardType.Password || options.keyboardType == KeyboardType.NumberPassword
        val surrounding = if (password) TextFieldValue() else surroundingText(value)
        val rect = request?.focusedRectInRoot?.invoke() ?: legacyRect
        val density = scale()
        val purpose = when (options.keyboardType) {
            KeyboardType.Password -> TextInputContentPurpose.Password
            KeyboardType.NumberPassword -> TextInputContentPurpose.Pin
            KeyboardType.Number, KeyboardType.Decimal -> TextInputContentPurpose.Number
            KeyboardType.Email -> TextInputContentPurpose.Email
            KeyboardType.Uri -> TextInputContentPurpose.Url
            KeyboardType.Phone -> TextInputContentPurpose.Phone
            else -> TextInputContentPurpose.Normal
        }
        val context = TextInputContext(
            surrounding.text,
            surrounding.text.codePointCount(0, surrounding.selection.end).toUShort(),
            surrounding.text.codePointCount(0, surrounding.selection.start).toUShort(),
            buildSet {
                if (password) { add(TextInputContentHint.HiddenText); add(TextInputContentHint.SensitiveData) }
                if (!options.singleLine) add(TextInputContentHint.Multiline)
                if (options.autoCorrect && !password) add(TextInputContentHint.Spellcheck)
            },
            purpose,
            LogicalRect.makeXYWH((rect.left / density).roundToInt(), (rect.top / density).roundToInt(),
                (rect.width / density).roundToInt().coerceAtLeast(1), (rect.height / density).roundToInt().coerceAtLeast(1)),
            fromIme,
        )
        if (!enabled) {
            application.textInputEnable(context)
            enabled = true
        } else if (context != lastContext) application.textInputUpdate(context)
        lastContext = context
        fromIme = false
    }

    fun receive(event: Event.TextInput) {
        if (!focused || !active) return
        fromIme = true
        edit(imeCommands(value, event.preeditStringData?.let {
            Preedit(it.text.orEmpty(), it.cursorBeginBytePos, it.cursorEndBytePos)
        }, event.commitStringData?.text, event.deleteSurroundingTextData?.let {
            it.beforeLengthInBytes.toLong() to it.afterLengthInBytes.toLong()
        }))
        // The request's value may lag behind onEditCommand until recomposition.
        // Its snapshot observer (or legacy updateState) sends the new context.
    }

    fun close() {
        focused = false
        request = null
        legacyEdit = null
    }
}

internal data class Preedit(val text: String, val cursorBegin: Int, val cursorEnd: Int)

// Build one atomic edit batch. Reading request.value() between two edit calls
// is wrong for value-based Compose fields: it can still contain the old text.
internal fun imeCommands(value: TextFieldValue, preedit: Preedit?, commit: String?, deletion: Pair<Long, Long>?): List<EditCommand> {
    val old = withoutPreedit(value)
    val commands = mutableListOf<EditCommand>()
    if (value.composition != null) commands += SetComposingTextCommand("", 1)
    commands += FinishComposingTextCommand()
    val before = old.text.substring(0, old.selection.min)
    val after = old.text.substring(old.selection.max)
    val beforeLength = utf8SuffixLength(before, deletion?.first ?: 0)
    val afterLength = utf8Offset(after, (deletion?.second ?: 0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    if (deletion != null) commands += DeleteSurroundingTextCommand(beforeLength, afterLength)
    if (commit != null) commands += CommitTextCommand(commit, 1)
    if (preedit != null && preedit.text.isNotEmpty()) {
        val start = old.selection.min - beforeLength + (commit?.length ?: 0)
        commands += SetComposingTextCommand(preedit.text, 1)
        if (preedit.cursorBegin >= 0 && preedit.cursorEnd >= 0) {
            commands += SetSelectionCommand(start + utf8Offset(preedit.text, preedit.cursorBegin), start + utf8Offset(preedit.text, preedit.cursorEnd))
        }
    }
    return commands
}

// Offsets on the wire are UTF-8 bytes; Compose uses UTF-16. Ignore a partial
// final codepoint rather than introduce a replacement character/split surrogate.
internal fun utf8Offset(text: String, byteOffset: Int): Int {
    var index = 0
    var bytes = 0
    while (index < text.length) {
        val next = text.offsetByCodePoints(index, 1)
        bytes += text.substring(index, next).toByteArray().size
        if (bytes > byteOffset) break
        index = next
    }
    return index
}

private fun utf8SuffixLength(text: String, byteCount: Long): Int {
    var index = text.length
    var bytes = 0L
    while (index > 0) {
        val next = text.offsetByCodePoints(index, -1)
        bytes += text.substring(next, index).toByteArray().size
        if (bytes > byteCount) break
        index = next
    }
    return text.length - index
}

internal fun withoutPreedit(value: TextFieldValue): TextFieldValue {
    val composition = value.composition ?: return value
    fun offset(index: Int) = when {
        index <= composition.min -> index
        index >= composition.max -> index - composition.length
        else -> composition.min
    }
    return TextFieldValue(value.text.removeRange(composition.min, composition.max), TextRange(offset(value.selection.start), offset(value.selection.end)))
}

// The protocol's total string limit is 4 KiB. Keep the selection and nearby
// complete codepoints, never cut a surrogate pair or UTF-8 sequence.
internal fun surroundingText(value: TextFieldValue): TextFieldValue {
    val plain = withoutPreedit(value)
    val text = plain.text
    var start = plain.selection.min
    var end = plain.selection.max
    var bytes = text.substring(start, end).toByteArray().size
    if (bytes > 3_500) return TextFieldValue()
    while (start > 0 || end < text.length) {
        var grew = false
        if (start > 0) {
            val next = text.offsetByCodePoints(start, -1)
            val extra = text.substring(next, start).toByteArray().size
            if (bytes + extra <= 3_500) { start = next; bytes += extra; grew = true }
        }
        if (end < text.length) {
            val next = text.offsetByCodePoints(end, 1)
            val extra = text.substring(end, next).toByteArray().size
            if (bytes + extra <= 3_500) { end = next; bytes += extra; grew = true }
        }
        if (!grew) break
    }
    return TextFieldValue(text.substring(start, end), TextRange(plain.selection.start - start, plain.selection.end - start))
}

package app.winters.octo.desktop.server

import app.winters.octo.desktop.settings.DesktopOs
import app.winters.octo.desktop.settings.currentOs
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference

// Tells when the computer's own addresses change, so a network join is heard
// instead of looked for.
fun interface AddressChanges {
    // Calls `onChange` from some thread whenever an address is added, removed
    // or changed, until the returned handle is closed. Null when this system
    // cannot tell, and the caller looks now and then instead.
    fun watch(onChange: () -> Unit): AutoCloseable?
}

private interface IpHelper : Library {
    // Registers `callback` for unicast address changes of a family (0 for
    // both IPv4 and IPv6); the handle comes back through `handle`. Zero is
    // success.
    fun NotifyUnicastIpAddressChange(
        family: Short,
        callback: Callback,
        context: Pointer?,
        initialNotification: Boolean,
        handle: PointerByReference,
    ): Int

    fun CancelMibChangeNotify2(handle: Pointer): Int
}

private fun interface AddressChangeCallback : Callback {
    fun invoke(context: Pointer?, row: Pointer?, type: Int)
}

private val ipHelper: IpHelper? by lazy { runCatching { Native.load("iphlpapi", IpHelper::class.java) }.getOrNull() }

// Windows' own address change notification (iphlpapi).
object WindowsAddressChanges : AddressChanges {
    override fun watch(onChange: () -> Unit): AutoCloseable? = runCatching {
        val api = ipHelper ?: return@runCatching null
        val callback = AddressChangeCallback { _, _, _ -> runCatching { onChange() } }
        val handle = PointerByReference()
        if (api.NotifyUnicastIpAddressChange(0, callback, null, false, handle) != 0) return@runCatching null
        val registered = handle.value ?: return@runCatching null
        var held: AddressChangeCallback? = callback
        AutoCloseable {
            // The callback stays referenced until Windows is told to stop
            // calling it.
            if (held != null) {
                runCatching { api.CancelMibChangeNotify2(registered) }
                held = null
            }
        }
    }.getOrNull()
}

fun systemAddressChanges(os: DesktopOs = currentOs()): AddressChanges =
    if (os == DesktopOs.Windows) WindowsAddressChanges else AddressChanges { null }

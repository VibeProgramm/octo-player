package app.winters.octo.wayland

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import org.jetbrains.desktop.linux.KeyCode
import org.jetbrains.desktop.linux.KeyModifiers

internal fun Set<KeyModifiers>.composeModifiers() = PointerKeyboardModifiers(
    isCtrlPressed = KeyModifiers.Control in this,
    isAltPressed = KeyModifiers.Alt in this,
    isShiftPressed = KeyModifiers.Shift in this,
    isMetaPressed = KeyModifiers.Logo in this,
)

// KDT reports XKB physical codes (evdev + 8); text itself comes from XKB/IME,
// not this map, so non-US layouts and supplementary Unicode stay intact.
internal fun composeKey(code: UInt): Key = keys[code] ?: Key.Unknown
private val keys = mapOf(
    KeyCode.Escape to Key.Escape, KeyCode.BackSpace to Key.Backspace, KeyCode.Delete to Key.Delete,
    KeyCode.Tab to Key.Tab, KeyCode.Return to Key.Enter, KeyCode.KP_Enter to Key.NumPadEnter,
    KeyCode.space to Key.Spacebar, KeyCode.Home to Key.MoveHome, KeyCode.End to Key.MoveEnd,
    KeyCode.Up to Key.DirectionUp, KeyCode.Down to Key.DirectionDown,
    KeyCode.Left to Key.DirectionLeft, KeyCode.Right to Key.DirectionRight,
    KeyCode.Page_Up to Key.PageUp, KeyCode.Page_Down to Key.PageDown, KeyCode.Insert to Key.Insert,
    KeyCode.Menu to Key.Menu, KeyCode.comma to Key.Comma,
    KeyCode.A to Key.A, KeyCode.B to Key.B, KeyCode.C to Key.C, KeyCode.D to Key.D,
    KeyCode.E to Key.E, KeyCode.F to Key.F, KeyCode.G to Key.G, KeyCode.H to Key.H,
    KeyCode.I to Key.I, KeyCode.J to Key.J, KeyCode.K to Key.K, KeyCode.L to Key.L,
    KeyCode.M to Key.M, KeyCode.N to Key.N, KeyCode.O to Key.O, KeyCode.P to Key.P,
    KeyCode.Q to Key.Q, KeyCode.R to Key.R, KeyCode.S to Key.S, KeyCode.T to Key.T,
    KeyCode.U to Key.U, KeyCode.V to Key.V, KeyCode.W to Key.W, KeyCode.X to Key.X,
    KeyCode.Y to Key.Y, KeyCode.Z to Key.Z,
    KeyCode.bracketleft to Key.LeftBracket, KeyCode.bracketright to Key.RightBracket,
    KeyCode.F1 to Key.F1, KeyCode.F2 to Key.F2, KeyCode.F3 to Key.F3, KeyCode.F4 to Key.F4,
    KeyCode.F5 to Key.F5, KeyCode.F6 to Key.F6, KeyCode.F7 to Key.F7, KeyCode.F8 to Key.F8,
    KeyCode.F9 to Key.F9, KeyCode.F10 to Key.F10, KeyCode.F11 to Key.F11, KeyCode.F12 to Key.F12,
)

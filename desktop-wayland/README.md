# Octo — native Wayland preview

The full Octo desktop player, hosted directly on Wayland with Kotlin Desktop
Toolkit 0.0.90, EGL/OpenGL ES and Skia. It reuses the real library, search,
Subsonic/Navidrome connection, Rust audio engine, full player and mini player.
No XWayland server is needed. Software renderers are rejected by default.

## Run

Download `Octo-Wayland-<version>-linux-x64.tar.gz` from
[this fork's releases](https://github.com/VibeProgramm/octo-player/releases), then:

```sh
tar -xzf Octo-Wayland-*-linux-x64.tar.gz
./Octo-Wayland/octo-wayland
```

Java 27 is included. Linux x86_64, glibc 2.39+ (Ubuntu 24.04+, current Arch/CachyOS),
a Wayland session, hardware EGL/OpenGL ES, ALSA/PulseAudio/PipeWire, libxkbcommon,
libwayland-client/egl and libsecret are required. File dialogs use the desktop's
`xdg-desktop-portal` backend. `DISPLAY` is unset by the launcher.

Use **F11** for fullscreen and **Ctrl+M** for the mini player. The same server
account and audio controls as the ordinary desktop app are available. To try it
with a separate profile:

```sh
OCTO_PROFILE_DIR="$HOME/.local/share/octo-wayland-preview" ./Octo-Wayland/octo-wayland
```

Otherwise it uses the usual Octo profile and single-instance lock. Close another
Octo instance first. Updates are installed by replacing the extracted bundle;
this preview cannot auto-install upstream's AWT build.

## Preview boundaries

- Native clipboard, keyboard/Unicode input, text-input-v3 IME, portal file
  chooser and incoming audio/list-file drops are implemented.
- Mini windows are recreated when changing between the main/mini player or
  selecting a preset size; the audio engine and playback queue stay alive.
  Placement and keep-above behavior belong to the compositor. Use its window
  menu/rules. Native tray and screen-reader integration are not implemented.
- KWin/AMD Mesa is the hardware validation target. GNOME, Intel/NVIDIA,
  physical monitor hotplug and suspend/resume are not yet validated. In
  particular KDT's NVIDIA EGL compatibility is still an upstream concern.
- Compositor decorations are preferred; a client header is used where those
  are unavailable. Import-page drops work without the AWT hover overlay.

This is an experimental prerelease, not a claim of desktop-platform parity.
Native diagnostics are written to `$XDG_CACHE_HOME/octo/wayland/native.log`
(normally `~/.cache/octo/wayland/native.log`).

## Build and verification

```sh
JAVA_HOME=/path/to/jdk-27 ./gradlew :desktop-wayland:test :desktop-wayland:packagePortable
```

JDK 17 is also used by the shared desktop modules. Packages are built and tested
by `.github/workflows/wayland-preview.yml`. CI uses explicit test-only software
EGL (`-Docto.wayland.testSoftware=true`); this is not hardware validation.
The bundled runtime is checked for real native audio, an advancing playback
clock, HTTPS, no X11 connections, and rejection of software rendering by default.

`check/native-session.py` runs against an isolated two-output KWin compositor.
It uses a local Subsonic fixture and independent Wayland keyboard/clipboard,
input-method and GTK drag-source clients, plus MPRIS to check the audio clock.
It never stores the fixture password in the keyring. See `check/run-native.sh`.

### Hardware acceptance (2026-10-30)

AMD Radeon Vega 8, Mesa 26.2.5, KWin 6.8.2, two isolated outputs at 100% and
150%, bundled Java 27. The real player passed sign-in, library/search, HTTP
streaming with an advancing MPRIS clock, pause/resume/seek, mini-player switching
while playing, mixed-DPI movement, fullscreen and clean shutdown. Independent
clients verified Unicode clipboard, text-input-v3 preedit/commit/UTF-8 deletion
and an audio-file drag from GTK. The real KDE portal completed a Save/Open
roundtrip with a Cyrillic filename. `strace` confirmed the Wayland socket and
DRM render node, with no X11 connection. With the full player paused and its
animated background enabled, an eight-second untraced sample used about 22%
of one CPU core (not a zero-animation idle measurement).

The full desktop test suite also runs on the Ubuntu CI runner. Local offscreen
visual tests are slow on this machine and have pre-existing image/locale
differences; the native adapter's seven regression tests pass locally.

### Compose dispatcher compatibility adapter

Compose 1.12.2 hardwires `PostDelayedDispatcher` to Skiko's Swing dispatcher.
That makes `RectManager` defragment its layout data on a different thread from
the native host, causing intermittent corruption. The host replaces the single
getter in `androidx.compose.ui.Actuals_desktopKt` with `Dispatchers.Main`, which
is bound to the Wayland event loop. The host jar **must be first** on the
classpath. Tests check the original ABI and actual delayed-callback dispatch.
Review/remove this small adapter when upstream exposes a host dispatcher here.

### Native application lifetime

KDT 0.0.90's `Application.close()` drops the Wayland event loop before its EGL
display; Ubuntu's Mesa 25 crashes in `libwayland-client` during that teardown.
The host therefore retains its single native `Application` until process exit.
It still stops the event loop, closes windows, flushes settings, and disposes
the player and Compose resources. The OS reclaims the process-level display
and runtime. Review this workaround when updating KDT.

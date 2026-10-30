# Probe results — 2026-10-30

## Rendering acceptance

Passed `check-gpu.sh` on Linux x86_64, OpenJDK 27.0.1.0.3, KWin 6.8.2,
Mesa 26.2.5, AMD Radeon Vega 8 (radeonsi/raven/ACO). The physical panel is
1920×1080 at 60 Hz, desktop scale 110%.

- `DISPLAY` absent and AWT headless; successful connection to the Wayland
  socket and open of `/dev/dri/renderD128` in `strace`.
- No attempted X11 socket or TCP display connection in the trace.
- EGL/OpenGL ES 3.2 hardware renderer, the production wash shader/filter chain
  and Compose text visible in a GPU readback PNG.
- Fullscreen configure acknowledged at physical 1920×1080, then restored to
  1100×748. Main window and two successive mini windows each rendered more
  than 60 frames. Clean termination with exit code 0.

The traced acceptance run had a fullscreen callback p50 of 16.80 ms, p95 of
32.22 ms and mean CPU draw/submit time of 4.05 ms. These numbers include tracing
and concurrent build/test activity; they are acceptance evidence, not a clean
performance benchmark.

A separate **untraced 30-second fullscreen run**, after local builds/tests had
finished, rendered 1776 frames. Across 1716 post-warm-up frames the callback
p50/p95 was **16.68/16.88 ms**, with **0.82 ms** mean CPU draw/submit. The whole
process (including startup/JIT) used 5.835 s user + 1.345 s system CPU over
31.742 s wall time: **22.6% of one CPU core**, peak RSS **243.8 MiB**. This is a
short rendering probe, not the full player's workload or a GPU timer result.

## Input, scaling and multiple outputs

Passed a separate isolated KWin virtual-backend session **using the same real
AMD GPU**, with two 1280×900 virtual outputs at 100% and 150%. The harness drove
KWin's native fake-input protocol, moved/resized the probe through KWin scripting,
and used `wl-copy`, `wl-paste` and a native GTK 4 drag source.

| Check | Result |
| --- | --- |
| Native key text and BackSpace | `octo` typed and independently read from clipboard |
| Clipboard both directions | Exact round trip of `octo · Привет 🌊` |
| Compose pointer input | Click counter advanced on both output scales |
| Move between outputs | Screen-change events and 1.0 → 1.5 → 1.0 scale changes |
| Resize | Framebuffer changed from 750×572 to 1050×858 at 150% |
| Minimize/restore | Window restored, accepted subsequent input/drop |
| External drag-and-drop | GTK text received as UTF-8, 20 bytes |
| Close | Native window closed and process exited successfully |

This verifies mixed-scale **virtual** output transitions, not physical monitor
hotplug or every display driver. In that untraced input run, 218 post-warm-up
750×572 frames had p50/p95 intervals 16.69/17.18 ms and mean CPU submit 1.14 ms.

## Remaining migration gates

- KWin advertises text-input-v3 and the probe enables it. The test session has
  no active input method, so real CJK composition, candidate placement, preedit
  selection and reconversion remain **unverified**. The diagnostic append-only
  inspector is not a complete Compose text-input adapter.
- No production keyboard focus/shortcuts, Compose clipboard/DnD adapter,
  screen-reader accessibility, tray integration, dialogs or client-side
  decorations have been ported to this host.
- The small second window verifies GPU/window lifetime, not the production
  mini-player's controls, always-on-top behavior or persisted placement.
- KDT 0.0.90 requests a compositor frame even when the callback draws nothing.
  Idle/minimized scheduling and GPU cache/lifecycle behavior need a production
  policy and longer soak tests.
- Intel/NVIDIA, GNOME, physical multi-monitor hotplug and suspend/resume have
  not been exercised. Upstream's open NVIDIA EGL issue is relevant to rollout:
  <https://github.com/JetBrains/kotlin-desktop-toolkit/issues/137>.

**Decision:** Java 27 + native Wayland + hardware Skia/Compose rendering is
feasible and demonstrated. Keep the host experimental. Replacing the packaged
player's windows now would lose integrations; the next implementation step is
the production Compose platform adapter and lifecycle coverage, then a gated
Linux backend option.

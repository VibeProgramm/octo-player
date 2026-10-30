# Dependency refresh and Java 27

Stable dependency review: 2026-10-30. Versions live in
`gradle/libs.versions.toml`, the Gradle wrapper, Cargo manifests/lockfiles and
`tools/cover-art/requirements.txt`.

| Component | Selected version |
| --- | --- |
| Desktop runtime, tests, jlink, jpackage | Java 27 |
| Shared/Android compilation | Java 17 |
| Gradle | 9.8.2, distribution SHA-256 checked |
| Kotlin and Compose compiler | 2.4.21 |
| Android Gradle Plugin | 9.5.0 |
| Compose Multiplatform | 1.12.2 |
| Skiko | Compose's matched 0.150.2 |
| Haze | 2.0.1 |
| OkHttp | 5.5.1 |
| kotlinx.serialization | 1.12.0 |
| DataStore / WorkManager | 1.3.0 / 2.12.0 |
| CameraX / Cast framework | 1.6.2 / 23.0.0 |
| ZXing / Palette / Robolectric | 3.5.4 / 1.1.0 / 4.17 |
| rubato / rtrb / ureq / rustls | 5.0.1 / 0.4.1 / 3.5.0 / 0.23.46 |
| objc2 / serde_json / fixture opus-rs | 0.6.5 / 1.0.154 / 0.1.40 |
| NumPy / Pillow | 2.5.4 / 13.0.0 |
| Kotlin Desktop Toolkit (probe only) | 0.0.90 |

## Compatibility choices

- Keep coherent stable library families and resolved transitive dependencies.
  A newer standalone Skiko is not a drop-in guarantee for Compose or Octo's
  reflection-based GPU cache management. In particular, do not force Skiko
  0.156 onto this Compose version.
- Keep the locally patched CPAL and Rust 1.88 MSRV. All three Cargo lockfiles
  are updated; `ureq`'s unversioned transport adapter now receives its clock,
  and the idle limiter preserves the new per-read/per-write timeout fields.
- Keep the existing JUnit 4 suite and its latest 4.x release. Jupiter is a test
  framework migration, not a compatible version-catalog bump.
- Gradle 9.8 supports running on Java 27. Kotlin 2.4.21's published fully
  supported integration range ends at Gradle 9.7 / AGP 9.3.1; this newer
  combination therefore needs the actual Android and desktop CI checks.
- Kotlin 2.4.21 does not emit Java 27 bytecode. Shared/desktop code stays at
  target 17; the FFM-based probe targets 25 while compiling and running with
  JDK 27. Bytecode target and installed runtime are separate choices.
- Standard OpenJDK 27 does not provide JBR's WLToolkit. Switching the runtime
  alone cannot make Compose/AWT a native Wayland GPU client. The separate
  [Wayland probe](../wayland-probe/README.md) owns the native EGL host.

## Reproducible checks

```sh
./gradlew :desktop:test :desktop-design:test :shared:core:desktopTest
./gradlew :shared:core:allTests :app:testDebugUnitTest :app:assembleDebug
cargo test --manifest-path audio-engine/Cargo.toml --locked
cargo check --manifest-path desktop/system-shim/Cargo.toml --locked
cargo check --manifest-path audio-engine/tools/make-fixtures/Cargo.toml --locked
./gradlew :wayland-probe:installDist
JAVA_HOME=/path/to/jdk-27 bash wayland-probe/check-gpu.sh /path/to/results
```

`desktop-installers.yml` installs JDK 17 and 27 and builds packages in Actions.
Linux package smoke tests require both `check: java 27` and successful HTTPS
from the actual packaged app. Those Xvfb/software smoke tests check packaging
and audio; the real-GPU acceptance script is a separate check.

`dependency-checks.yml` builds/tests Android with only JDK 17, runs Rust tests,
compiles the Wayland probe on Java 27, and renders small covers with the new
NumPy/Pillow. A Linux system-shim check does not test macOS or Windows code;
Windows packaging exercises that platform in the installer workflow.

## Local verification

- Desktop and test sources compile; the Wayland probe builds and passes the
  real-GPU acceptance and native input/mixed-scale checks described in its
  [results](../wayland-probe/RESULTS.md).
- Rust audio: 209 passed. Linux system shim and fixture generator: `cargo
  check --locked` passed. NumPy/Pillow: dependency check and both liquid/silk
  cover generation passed.
- Shared desktop tests: 1159 passed. Desktop design: 50 passed. Desktop: 1272
  tests, 56 skipped, two failures (`ContrastTest` yellow-wash contrast and
  `TitleRowTest` heart alignment). Both failure cases were reproduced on the
  pre-update baseline on this machine; the title alignment measurements are
  identical. Tests have not been disabled or their thresholds relaxed.
- `createDistributable` succeeded. The generated runtime's `release` file
  reports Java 27.0.1.0.3. Launching that image with an isolated profile and the
  audio smoke option printed `check: java 27`, advancing real audio playback,
  and `check: https works (h2, HTTP 200)`; process exit was 0.
- Running Gradle itself on JDK 17 with `:desktop:run -Pocto.checkPlay=...`
  also launched the application on Java 27 and passed audio/HTTPS checks.

## GitHub Actions verification

- [Desktop installers, run 40996218327](https://github.com/VibeProgramm/octo-player/actions/runs/40996218327):
  **passed** Linux and Windows desktop tests; built MSI, two portable ZIPs,
  DEB, RPM, AppImage, Flatpak and pacman packages; Linux launch/audio/Java 27/
  HTTPS smoke tests and release manifest/checksum rehearsal passed.
- [Dependency checks, run 40997192683](https://github.com/VibeProgramm/octo-player/actions/runs/40997192683):
  **passed** shared `allTests`, Android unit tests and debug APK build with
  only JDK 17; Rust's 209 tests; Java 27 probe compilation; both Python cover
  renderers. Android's repository calls the API 37 base platform package
  `platforms;android-37.0`, not `platforms;android-37`.

Artifacts were built and uploaded by Actions. These verification runs did not
publish a release. Subsequent documentation-only commits record these results.

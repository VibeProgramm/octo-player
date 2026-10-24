package app.winters.octo.desktop.system

import app.winters.octo.audio.processStats
import app.winters.octo.desktop.AppState
import app.winters.octo.desktop.audio.EnginePlayer
import app.winters.octo.desktop.copyWords
import app.winters.octo.desktop.settings.AppPlaces
import app.winters.octo.desktop.settings.AppSettings
import app.winters.octo.desktop.settings.DesktopOs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.swing.SwingUtilities

// What the app holds at one moment: memory, handles, threads, graphics card
// memory, CPU and I/O, the Java heap and how often the audio device reopened.
// A field is null when it could not be read.
data class MemoryReading(
    val privateMb: Long?,
    val workingSetMb: Long?,
    val handles: Long?,
    val threads: Long?,
    val gpuDedicatedMb: Long?,
    val gpuSharedMb: Long?,
    val ioOtherMb: Long?,
    val cpuSeconds: Long?,
    val heapUsedMb: Long,
    val heapCommittedMb: Long,
    val deviceReopens: Long?,
)

private const val MB = 1024L * 1024L

// The counters now: the engine library's for the process, the JVM's for the
// heap and CPU time.
fun readMemory(reopens: () -> Long?): MemoryReading {
    val stats = runCatching { processStats() }.getOrNull()
    val cpuSeconds = runCatching { ProcessHandle.current().info().totalCpuDuration().map { it.seconds }.orElse(null) }.getOrNull()
    val heap = runCatching {
        val runtime = Runtime.getRuntime()
        (runtime.totalMemory() - runtime.freeMemory()) / MB to runtime.totalMemory() / MB
    }.getOrNull()
    return MemoryReading(
        privateMb = runCatching { stats?.privateBytes?.let { it.toLong() / MB } }.getOrNull(),
        workingSetMb = runCatching { stats?.workingSet?.let { it.toLong() / MB } }.getOrNull(),
        handles = runCatching { stats?.handles?.toLong() }.getOrNull(),
        threads = runCatching { stats?.threads?.toLong() }.getOrNull(),
        gpuDedicatedMb = runCatching { stats?.gpuDedicated?.let { it.toLong() / MB } }.getOrNull(),
        gpuSharedMb = runCatching { stats?.gpuShared?.let { it.toLong() / MB } }.getOrNull(),
        ioOtherMb = runCatching { stats?.ioOtherBytes?.let { it.toLong() / MB } }.getOrNull(),
        cpuSeconds = cpuSeconds,
        heapUsedMb = heap?.first ?: 0L,
        heapCommittedMb = heap?.second ?: 0L,
        deviceReopens = runCatching(reopens).getOrNull(),
    )
}

// One log line for a reading, as the memory log writes it.
fun memoryLine(time: String, minute: Long, r: MemoryReading, window: String, playing: Boolean): String =
    "$time min=$minute private=${r.privateMb ?: "?"} ws=${r.workingSetMb ?: "?"} handles=${r.handles ?: "?"} " +
        "threads=${r.threads ?: "?"} gpu=${r.gpuDedicatedMb ?: "?"}/${r.gpuSharedMb ?: "?"} cpu=${r.cpuSeconds ?: "?"} " +
        "io=${r.ioOtherMb ?: "?"} heap=${r.heapUsedMb}/${r.heapCommittedMb} reopens=${r.deviceReopens ?: "?"} " +
        "window=$window playing=${if (playing) "yes" else "no"}"

// A rolling log file in a folder: when memory.log passes the cap it becomes
// memory.log.1 (the one before is dropped), so two files hold the recent past.
class MemoryLog(private val dir: File, private val capBytes: Long = 512L * 1024) {
    private val current get() = File(dir, "memory.log")
    private val older get() = File(dir, "memory.log.1")

    // Adds a line; a failure to write is let go.
    fun append(line: String) {
        runCatching {
            dir.mkdirs()
            if (current.length() > capBytes) {
                older.delete()
                current.renameTo(older)
            }
            current.appendText(line + "\n", Charsets.UTF_8)
        }
    }

    // The last n lines, oldest first, across both files.
    fun lastLines(n: Int): List<String> = runCatching {
        (listOf(older, current).filter { it.isFile }.flatMap { it.readLines(Charsets.UTF_8) }).takeLast(n)
    }.getOrDefault(emptyList())
}

// The drawing surface inside a window, found by walking its components.
internal fun findSkiaLayer(window: java.awt.Window): org.jetbrains.skiko.SkiaLayer? {
    fun find(component: java.awt.Component): org.jetbrains.skiko.SkiaLayer? {
        if (component is org.jetbrains.skiko.SkiaLayer) return component
        if (component is java.awt.Container) component.components.forEach { child -> find(child)?.let { return it } }
        return null
    }
    return find(window)
}

// The graphics API and the first lines of what the drawing reports about the
// graphics card; null until the drawing is up.
fun rendererText(window: java.awt.Window): String? {
    fun read(): String? {
        val layer = findSkiaLayer(window) ?: return null
        val info = layer.renderInfo
        if (info.contains("isn't initialized")) return null
        return "${layer.renderApi} | " + info.lineSequence().map(String::trim).filter(String::isNotEmpty).take(4).joinToString(" | ")
    }
    return runCatching {
        if (SwingUtilities.isEventDispatchThread()) {
            read()
        } else {
            val f = java.util.concurrent.CompletableFuture<String?>()
            SwingUtilities.invokeLater { f.complete(runCatching { read() }.getOrNull()) }
            f.get(2, java.util.concurrent.TimeUnit.SECONDS)
        }
    }.getOrNull()
}

// The folder the memory log lives in: beside the cache folder on Windows,
// inside it elsewhere.
fun logFolder(os: DesktopOs, places: AppPlaces = AppPlaces.forSystem()): File =
    if (os == DesktopOs.Windows) File(places.cache.parentFile, "logs") else File(places.cache, "logs")

// A plain-text report to paste into a bug report: version, system, graphics,
// uptime, memory now, the settings that matter and the recent memory log. It
// holds no server address, user name or other account detail.
fun bugDetails(
    version: String?,
    renderer: String?,
    uptimeMs: Long,
    reading: MemoryReading,
    settings: List<Pair<String, String>>,
    logLines: List<String>,
): String {
    val minutes = uptimeMs / 60_000
    val fields = memoryLine("", 0, reading, "", false).substringAfter("private=").substringBefore(" window=")
    return buildString {
        appendLine("Octo ${version ?: "unknown"}")
        appendLine("${System.getProperty("os.name")} ${System.getProperty("os.version")}, Java ${System.getProperty("java.version")}")
        appendLine("Renderer: ${renderer ?: "unknown"}")
        appendLine("Up for ${minutes / 60} h ${minutes % 60} min")
        appendLine("Memory now: private=$fields")
        appendLine("Settings: ${settings.joinToString(", ") { (name, value) -> "$name=$value" }}")
        appendLine()
        appendLine("Memory log (newest last):")
        logLines.forEach(::appendLine)
    }
}

// Puts the bug report on the clipboard, with a notice that it is there.
suspend fun copyBugDetails(app: AppState, window: java.awt.Window?, logs: File) {
    val text = withContext(Dispatchers.IO) {
        runCatching {
            val renderer = window?.let(::rendererText) ?: java.awt.Window.getWindows().firstNotNullOfOrNull(::rendererText)
            bugDetails(
                version = System.getProperty("octo.version"),
                renderer = renderer,
                uptimeMs = runCatching {
                    ProcessHandle.current().info().startInstant()
                        .map { java.time.Duration.between(it, java.time.Instant.now()).toMillis() }.orElse(0L)
                }.getOrDefault(0L),
                reading = readMemory { (app.player as? EnginePlayer)?.engine?.deviceReopens() },
                settings = bugSettings(app.settings.current, app.library?.index?.songs?.size),
                logLines = MemoryLog(logs).lastLines(24),
            )
        }.getOrNull()
    }
    if (text != null) {
        withContext(Dispatchers.Main) { app.copyWords(text, "Copied the details.") }
    }
}

// The settings a bug report names: choices only, never an address, name or
// certificate.
internal fun bugSettings(settings: AppSettings, songs: Int?): List<Pair<String, String>> = buildList {
    add("animated art" to settings.appearance.animatedArt)
    add("ambience" to settings.appearance.ambience.name)
    add("wash moving" to settings.appearance.wash.moving.toString())
    add("ambient glow" to settings.appearance.ambientGlow.toString())
    add("Discord status" to settings.discord.on.toString())
    add("close to tray" to settings.system.closeToTray.toString())
    add("mini player open" to settings.system.miniPlayerOpen.toString())
    add("start in tray" to settings.system.startInTray.toString())
    add("side panel" to (settings.sidePanel ?: "none"))
    songs?.let { add("library songs" to it.toString()) }
}

private fun nowText(): String =LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString()

// Writes a start line, then one memory line a minute in and every 10 minutes
// after, and the renderer once it is known. Nothing here throws into the app.
fun startMemoryLog(
    scope: CoroutineScope,
    log: MemoryLog,
    renderer: () -> String?,
    state: () -> Pair<String, Boolean>,
    reopens: () -> Long?,
) {
    scope.launch(Dispatchers.IO) {
        runCatching {
            log.append(
                "${nowText()} start version=${System.getProperty("octo.version")} " +
                    "os=${System.getProperty("os.name")} ${System.getProperty("os.version")} java=${System.getProperty("java.version")}",
            )
            var rendererLogged = false
            val started = System.currentTimeMillis()
            delay(60_000)
            while (true) {
                runCatching {
                    val (window, playing) = state()
                    val minute = (System.currentTimeMillis() - started) / 60_000
                    log.append(memoryLine(nowText(), minute, readMemory(reopens), window, playing))
                    if (!rendererLogged) {
                        renderer()?.let {
                            log.append("${nowText()} renderer $it")
                            rendererLogged = true
                        }
                    }
                }
                delay(10 * 60_000L)
            }
        }
    }
}

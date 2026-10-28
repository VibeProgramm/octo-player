package app.winters.octo.desktop.ui

import app.winters.octo.audio.processStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.DirectContext
import org.jetbrains.skiko.SkiaLayer
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities

// Empties each window's graphics card cache now and then. Skia keeps spare
// blocks there that the card holds on to; with the limit at nothing for a
// moment they go, and the limit is put back. It runs every `everyMs`, and
// soon after `soon()` (a window hid, minimized or handed over to the mini
// player), at most once per `debounceMs`. After the trim the cache is held
// empty for `settleMs`, and what the card gave back is read.
internal class GpuCacheTrimmer(
    private val trim: suspend () -> Int,
    private val readGpuMb: () -> Long?,
    private val everyMs: Long = 5 * 60_000L,
    private val debounceMs: Long = 10_000L,
    private val settleMs: Long = 2_000L,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // How many trims emptied at least one window's cache.
    val trims = AtomicLong()

    // What the last trim freed, in MB (negative when a shown window drew more
    // meanwhile); null when the card's use could not be read.
    @Volatile var lastFreedMb: Long? = null

    private val requests = Channel<Unit>(Channel.CONFLATED)

    // Asks for a trim soon; never blocks, and does nothing before `start`.
    fun soon() {
        if (started) requests.trySend(Unit)
    }

    @Volatile private var started = false

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch(dispatcher) {
            var lastAt: Long? = null
            while (true) {
                val asked = withTimeoutOrNull(everyMs) { requests.receive() } != null
                if (asked) {
                    // The window's last frames land first.
                    delay(settleMs)
                    lastAt?.let { at ->
                        val wait = at + debounceMs - clock()
                        if (wait > 0) delay(wait)
                    }
                    requests.tryReceive()
                }
                lastAt = clock()
                runCatching {
                    val before = readGpuMb()
                    val n = trim()
                    delay(settleMs)
                    val after = readGpuMb()
                    if (n > 0) {
                        trims.incrementAndGet()
                        lastFreedMb = if (before != null && after != null) before - after else null
                    }
                }.onFailure { if (it is CancellationException) throw it }
            }
        }
    }
}

// The trimmer the app runs, on the windows' real caches. The system property
// `octo.gpuTrim=off` turns it off.
object GpuCacheTrim {
    private val trimmer = GpuCacheTrimmer(
        trim = { trimAllWindows() },
        readGpuMb = {
            runCatching {
                processStats().let { ((it.gpuDedicated ?: 0u) + (it.gpuShared ?: 0u)).toLong() / (1024 * 1024) }
            }.getOrNull()
        },
    )

    val trims: AtomicLong get() = trimmer.trims
    val lastFreedMb: Long? get() = trimmer.lastFreedMb

    private fun off() = System.getProperty("octo.gpuTrim") == "off"

    fun start(scope: CoroutineScope) {
        if (!off()) trimmer.start(scope)
    }

    fun soon() {
        if (!off()) trimmer.soon()
    }
}

private enum class Api { Direct3D, OpenGl, Angle }

private class Renderer(val redrawer: Any, val api: Api)

private const val REDRAWERS = "org.jetbrains.skiko.redrawer."

private fun layersIn(component: java.awt.Component): List<SkiaLayer> = when (component) {
    is SkiaLayer -> listOf(component)
    is java.awt.Container -> component.components.flatMap(::layersIn)
    else -> emptyList()
}

// Every drawing surface of every window, collected with the tree locked and
// released before anything is done with them.
private fun allLayers(): List<SkiaLayer> = runCatching {
    java.awt.Window.getWindows().flatMap { window -> synchronized(window.treeLock) { layersIn(window) } }
}.getOrDefault(emptyList())

// The layer's redrawer when it draws with the card through a kind this code
// knows; null for software drawing or anything else.
private fun rendererOf(layer: SkiaLayer): Renderer? = runCatching {
    val redrawer = layer.javaClass.getMethod("getRedrawer\$skiko").invoke(layer) ?: return@runCatching null
    val api = when (redrawer.javaClass.name) {
        REDRAWERS + "Direct3DRedrawer" -> Api.Direct3D
        REDRAWERS + "WindowsOpenGLRedrawer" -> Api.OpenGl
        REDRAWERS + "AngleRedrawer" -> Api.Angle
        else -> return@runCatching null
    }
    Renderer(redrawer, api)
}.getOrNull()

private fun field(owner: Any, name: String): Any? =
    owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

private fun isDisposed(redrawer: Any): Boolean =
    Class.forName(REDRAWERS + "AWTRedrawer").getDeclaredMethod("isDisposed").apply { isAccessible = true }.invoke(redrawer) == true

// The redrawer's Skia context, handed to `use` unless the redrawer is
// disposed or the context closed. The caller holds whatever the renderer needs.
private fun <T : Any> withDirectContext(redrawer: Any, use: (DirectContext) -> T): T? {
    if (isDisposed(redrawer)) return null
    val handler = field(redrawer, "contextHandler") ?: return null
    val context = Class.forName("org.jetbrains.skiko.context.ContextHandler").getDeclaredMethod("getContext")
        .apply { isAccessible = true }.invoke(handler) as? DirectContext ?: return null
    if (context.isClosed) return null
    return use(context)
}

// Runs `use` on the layer's context the way its renderer allows, null when it
// could not be reached.
//  - Direct3D records frames on the Swing thread but replays them on another
//    thread under the redrawer's drawLock (as it creates the context, resizes
//    and disposes), so the context is touched off the Swing thread under that
//    lock; on the Swing thread it never is, it crashed the process.
//  - OpenGL draws on the Swing thread with its context current, so the
//    context is made current there first.
//  - ANGLE draws on the Swing thread and takes drawLock with its device made
//    current first, as it does.
private suspend fun <T : Any> SkiaLayer.reach(use: (DirectContext) -> T): T? {
    val renderer = rendererOf(this) ?: return null
    return try {
        when (renderer.api) {
            Api.Direct3D -> withContext(Dispatchers.Default) {
                runCatching {
                    if (SwingUtilities.isEventDispatchThread()) return@runCatching null
                    val lock = field(renderer.redrawer, "drawLock") ?: return@runCatching null
                    synchronized(lock) { withDirectContext(renderer.redrawer, use) }
                }.getOrNull()
            }
            Api.OpenGl -> withContext(Dispatchers.Main) {
                runCatching {
                    if (isDisposed(renderer.redrawer)) return@runCatching null
                    renderer.redrawer.javaClass.getDeclaredMethod("makeCurrent").apply { isAccessible = true }.invoke(renderer.redrawer)
                    withDirectContext(renderer.redrawer, use)
                }.getOrNull()
            }
            Api.Angle -> withContext(Dispatchers.Main) {
                runCatching {
                    val lock = field(renderer.redrawer, "drawLock") ?: return@runCatching null
                    synchronized(lock) {
                        if (isDisposed(renderer.redrawer)) return@runCatching null
                        val device = field(renderer.redrawer, "device") as? Long ?: return@runCatching null
                        Class.forName(REDRAWERS + "AngleRedrawerKt").getMethod("access\$makeCurrent", java.lang.Long.TYPE).invoke(null, device)
                        withDirectContext(renderer.redrawer, use)
                    }
                }.getOrNull()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }
}

// Empties every window's graphics card cache: each one's limit is set to
// nothing, all are held empty for `holdMs` with no lock held, then each limit
// is put back. How many windows were emptied.
suspend fun trimAllWindows(holdMs: Long = 2_000L): Int {
    val emptied = allLayers().mapNotNull { layer ->
        layer.reach { context ->
            val limit = context.resourceCacheLimit
            context.resourceCacheLimit = 0
            limit
        }?.let { layer to it }
    }
    try {
        delay(holdMs)
    } finally {
        withContext(NonCancellable) {
            emptied.forEach { (layer, limit) -> layer.reach { context -> context.resourceCacheLimit = limit } }
        }
    }
    return emptied.size
}

// Sets every window's cache limit, in bytes.
suspend fun setLimitAll(bytes: Long) {
    allLayers().forEach { layer -> layer.reach { context -> context.resourceCacheLimit = bytes } }
}

// The first window's cache limit, in bytes; null when it draws without the
// card or its insides differ.
suspend fun limitOfFirst(): Long? =
    allLayers().firstNotNullOfOrNull { layer -> layer.reach { context -> context.resourceCacheLimit } }

package app.winters.octo.ui.playlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.winters.octo.catalog.TrackEntity
import app.winters.octo.catalog.isFind
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoIcons
import app.winters.octo.ui.common.MoreItem
import app.winters.octo.design.OctoSwitch
import app.winters.octo.design.OctoType
import app.winters.octo.offline.DownloadEntity
import app.winters.octo.offline.DownloadStatus
import app.winters.octo.offline.OfflineDownloads
import app.winters.octo.offline.OfflinePrefs
import app.winters.octo.offline.OfflineSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class KeepDownloadedViewModel @Inject constructor(
    private val offline: OfflineDownloads,
    settings: OfflineSettings,
) : ViewModel() {
    val prefs: StateFlow<OfflinePrefs> =
        settings.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OfflinePrefs())
    val kept: StateFlow<Map<String, DownloadEntity>> = offline.byTrack

    fun setPlaylistKept(id: String, on: Boolean) = offline.setPlaylistKept(id, on)
    fun setLikedKept(on: Boolean) = offline.setKeepLiked(on)
}

// The line under a switched-on rule: how many of its songs are on the phone.
fun keptSummary(serverOnly: Int, done: Int): String = when {
    serverOnly == 0 -> "Every song is already on this phone"
    done >= serverOnly -> "All $serverOnly downloaded"
    else -> "$done of $serverOnly downloaded"
}

// "Keep downloaded" for a playlist: its songs only on a server are
// downloaded to the phone, and the downloads follow the playlist as songs
// come and go. Shown only when there is something to download or it is on.
@Composable
fun KeepPlaylistDownloaded(playlistId: String, tracks: List<TrackEntity>, vm: KeepDownloadedViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    KeepDownloadedLine(playlistId in prefs.keptPlaylists, tracks, vm) { vm.setPlaylistKept(playlistId, it) }
}

// The same for Liked songs, the switch the Offline settings have too.
@Composable
fun KeepLikedDownloaded(tracks: List<TrackEntity>, vm: KeepDownloadedViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    KeepDownloadedLine(prefs.keepLiked, tracks, vm, vm::setLikedKept)
}

// The words of a Keep downloaded switch: its quiet line, how many are on
// the phone while it is on and what it does while it is off.
const val KEEP_DOWNLOADED = "Keep downloaded"
const val KEEP_DOWNLOADED_OFF = "Download the songs only on your server, and keep them in step"

fun keepDetail(checked: Boolean, serverOnly: Int, done: Int): String = if (checked) keptSummary(serverOnly, done) else KEEP_DOWNLOADED_OFF

// Keep downloaded for a playlist as a switch in its header's More menu,
// or nothing when there is nothing to download and it is off.
@Composable
fun keepPlaylistItem(playlistId: String, tracks: List<TrackEntity>, vm: KeepDownloadedViewModel = hiltViewModel()): MoreItem? {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    return keepItem(playlistId in prefs.keptPlaylists, tracks, vm) { vm.setPlaylistKept(playlistId, it) }
}

// The same for Liked songs.
@Composable
fun keepLikedItem(tracks: List<TrackEntity>, vm: KeepDownloadedViewModel = hiltViewModel()): MoreItem? {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    return keepItem(prefs.keepLiked, tracks, vm, vm::setLikedKept)
}

@Composable
private fun keepItem(checked: Boolean, tracks: List<TrackEntity>, vm: KeepDownloadedViewModel, onChange: (Boolean) -> Unit): MoreItem? {
    val kept by vm.kept.collectAsStateWithLifecycle()
    val serverOnly = tracks.filter { !it.onPhone && !isFind(it.id) }
    if (serverOnly.isEmpty() && !checked) return null
    val done = serverOnly.count { kept[it.id]?.state == DownloadStatus.Done }
    return MoreItem(KEEP_DOWNLOADED, OctoIcons.Download, detail = keepDetail(checked, serverOnly.size, done), checked = checked) { onChange(!checked) }
}

// While Keep downloaded is on and songs are still coming, how far it has
// got, for a page's details line. Nothing once they are all on the phone.
@Composable
fun keepPlaylistProgress(playlistId: String, tracks: List<TrackEntity>, vm: KeepDownloadedViewModel = hiltViewModel()): String? {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    return keepProgress(playlistId in prefs.keptPlaylists, tracks, vm)
}

@Composable
private fun keepProgress(checked: Boolean, tracks: List<TrackEntity>, vm: KeepDownloadedViewModel): String? {
    val kept by vm.kept.collectAsStateWithLifecycle()
    if (!checked) return null
    val serverOnly = tracks.filter { !it.onPhone && !isFind(it.id) }
    val done = serverOnly.count { kept[it.id]?.state == DownloadStatus.Done }
    return keepingNote(serverOnly.size, done)
}

// The progress note while songs are still on their way, or nothing.
fun keepingNote(serverOnly: Int, done: Int): String? = if (serverOnly > 0 && done < serverOnly) keptSummary(serverOnly, done) else null

@Composable
private fun KeepDownloadedLine(checked: Boolean, tracks: List<TrackEntity>, vm: KeepDownloadedViewModel, onChange: (Boolean) -> Unit) {
    val kept by vm.kept.collectAsStateWithLifecycle()
    val serverOnly = tracks.filter { !it.onPhone && !isFind(it.id) }
    if (serverOnly.isEmpty() && !checked) return
    val done = serverOnly.count { kept[it.id]?.state == DownloadStatus.Done }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Keep downloaded", style = OctoType.bodySmall, color = OctoColors.TextPrimary)
            Text(
                if (checked) keptSummary(serverOnly.size, done) else "Download the songs only on your server, and keep them in step",
                style = OctoType.caption,
                color = OctoColors.TextMuted,
            )
        }
        OctoSwitch(checked = checked, onCheckedChange = onChange)
    }
}

import Foundation
import Observation
import OctoKit

/// How long after an edit it is sent, so quick changes go as one.
private let sendDelayMs: Int64 = 1_500

extension PlaylistRow {
    /// Changed on the phone since both sides last agreed.
    var edited: Bool { syncedAt == nil || updatedAt > (syncedAt ?? 0) }

    var linked: LinkedPlaylist? {
        serverId.map { LinkedPlaylist(id, $0, name, edited, syncedName, serverStamp, family: family) }
    }
}

/// Keeps playlists in step with the signed-in server. The server's playlists come to the phone
/// after each copy of its library; a linked playlist edited on the phone goes to the server
/// shortly after, and again after the next copy if that failed. Playlists only on the phone stay
/// there unless the listener saves one to the server or turns on saving new ones. Nothing here
/// ever shows an error.
@MainActor
@Observable
final class PlaylistSync {
    /// Whether playlists made on the phone are made on the server too. Off until turned on.
    private(set) var newOnServer: Bool

    @ObservationIgnored private let session: Session
    @ObservationIgnored private let library: Library
    @ObservationIgnored private let discovery: ServerDiscovery
    @ObservationIgnored private let prefs = Prefs(ServerSyncChoices.playlistsStore)
    @ObservationIgnored private let choices = ServerSyncChoices()
    @ObservationIgnored private var choiceWatch: NSObjectProtocol?
    /// One change to the server's playlists at a time.
    @ObservationIgnored private var running: Task<Void, Never>?
    /// Edits waiting to be sent, by playlist.
    @ObservationIgnored private var waits: [String: Task<Void, Never>] = [:]
    /// When the server's playlists were last brought together with the phone's.
    @ObservationIgnored private var reconciledAt: Int64 = 0
    /// Reads the signed-in account as the family sees it, loading it when not loaded yet (set by AccountHooks).
    @ObservationIgnored var familyPlan: @MainActor () async -> FamilyMe? = { nil }

    init(session: Session, library: Library, discovery: ServerDiscovery, sync: ServerSync) {
        self.session = session
        self.library = library
        self.discovery = discovery
        newOnServer = choices.newPlaylistsOnServer
        // A backup put back sets the choice in the same place; the switch follows it.
        choiceWatch = NotificationCenter.default.addObserver(forName: UserDefaults.didChangeNotification, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated {
                guard let self, self.newOnServer != self.choices.newPlaylistsOnServer else { return }
                self.newOnServer = self.choices.newPlaylistsOnServer
            }
        }
        sync.afterSync("playlists") { [weak self] in await self?.afterSync() }
    }

    /// Whether a server is signed in, so a playlist can be saved to it.
    var available: Bool { session.inUse != nil }

    func setNewOnServer(_ on: Bool) {
        newOnServer = on
        choices.setNewPlaylistsOnServer(on)
    }

    /// Runs `work` after any change already running, so changes to the server go one at a time.
    private func serially(_ work: @escaping @MainActor () async -> Void) async {
        let before = running
        let task = Task { @MainActor in
            await before?.value
            await work()
        }
        running = task
        await task.value
    }

    /// Runs after each copy of the server's library.
    func afterSync() async {
        guard let server = await signedIn() else { return }
        reconciledAt = epochNowMs()
        await serially { await self.reconcile(server) }
    }

    /// The playlists are on screen: one deleted or made on another device since the last copy of
    /// the library is taken out or brought in now, at most once a minute.
    func freshen() {
        let now = epochNowMs()
        guard playlistsStale(now, reconciledAt) else { return }
        reconciledAt = now
        Task {
            guard let server = await signedIn() else { return }
            await serially { await self.reconcile(server) }
        }
    }

    /// A playlist was made on the phone. With saving new playlists turned on, it is made on the
    /// server too.
    func created(_ localId: String) {
        guard newOnServer else { return }
        saveToServer(localId)
    }

    /// Makes this playlist on the server too, and keeps the two in step.
    func saveToServer(_ localId: String) {
        Task {
            guard let server = await signedIn() else { return }
            try? await library.store.keepOnServer(localId, sourceId: server.sourceId)
            await send(localId)
        }
    }

    /// A playlist changed on the phone. One kept with the server is sent shortly; one only on the
    /// phone is left alone.
    func changed(_ localId: String) {
        waits[localId]?.cancel()
        waits[localId] = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(sendDelayMs))
            if Task.isCancelled { return }
            self?.waits[localId] = nil
            // Once started, a send runs to the end.
            await Task { await self?.send(localId) }.value
        }
    }

    /// A playlist was deleted on the phone. One kept with the server is deleted there too, now or
    /// after the next copy.
    func deleted(_ playlist: PlaylistRow) {
        guard let serverId = playlist.serverId else { return }
        Task {
            guard let server = await signedIn(), playlist.sourceId == server.sourceId else { return }
            addDeleted(server.account, serverId)
            await serially { _ = await failureOf { try await self.deleteOnServer(server, serverId) } }
        }
    }

    /// Forgets a server taken off the list: its deletions still waiting, and its claim on its address.
    func forget(_ account: String, sourceId: String?) {
        prefs.forgetAccount(account)
        if let sourceId, prefs.string("linked_by", sourceId) == account { prefs.remove("linked_by", sourceId) }
    }

    private func send(_ localId: String) async {
        guard let server = await signedIn() else { return }
        await serially { [weak self] in
            guard let self, let playlist = try? await self.library.store.playlist(localId), playlist.sourceId == server.sourceId else { return }
            _ = await failureOf {
                if playlist.serverId == nil {
                    try await self.create(server, playlist)
                } else if playlist.edited {
                    try await self.push(server, playlist)
                }
            }
        }
    }

    private func reconcile(_ server: ServerInUse) async {
        let client = server.client
        guard let all = try? await client.fullPlaylists() else { return }
        let suspects = stationSuspects(all)
        // Without the list of stations, a playlist that might be one is left out.
        var stations = Set<String>()
        if !suspects.isEmpty {
            if let listed = try? await client.stations() { stations = Set(listed.map(\.id)) } else { stations = Set(suspects.map(\.id)) }
        }
        let keep = importable(all, stationIds: stations, username: server.username)
        let store = library.store
        let kept = (try? await store.serverPlaylists(server.sourceId)) ?? []
        let keepIds = Set(keep.map(\.id))
        let steps = planPlaylists(
            server: keep.map { ServerPlaylist($0.id, $0.name, stampOf($0.changed, $0.songCount)) },
            others: Set(all.map(\.id)).subtracting(keepIds),
            linked: kept.compactMap(\.linked),
            waiting: kept.filter { $0.serverId == nil }.map(\.id),
            deleted: deleted(server.account))
        for step in steps { _ = await failureOf { try await self.run(server, step) } }
        var notices: [String: String?] = [:]
        for playlist in keep { notices[playlist.id] = playlist.octoNotice }
        for row in (try? await store.serverPlaylists(server.sourceId)) ?? [] {
            guard let serverId = row.serverId, let notice = notices[serverId], notice != row.octoNotice else { continue }
            try? await store.markNotice(row.id, notice: notice)
        }
        // Which of them the server lists as the family's.
        var families: [String: Bool] = [:]
        for playlist in keep { families[playlist.id] = isFamilyPlaylist(owner: playlist.owner, username: server.username) }
        for row in (try? await store.serverPlaylists(server.sourceId)) ?? [] {
            guard let serverId = row.serverId, let isFamily = families[serverId], isFamily != row.family else { continue }
            try? await store.markFamily(row.id, family: isFamily)
        }
    }

    private func run(_ server: ServerInUse, _ step: PlaylistStep) async throws {
        let store = library.store
        switch step {
        case .importing(let serverId, let stamp):
            try await importPlaylist(server, serverId, stamp)
        case .pull(let localId, let serverId, let stamp, let keepName):
            try await pull(server, localId, serverId, stamp, keepName)
        case .push(let localId, _):
            if let playlist = try await store.playlist(localId) { try await push(server, playlist) }
        case .create(let localId):
            if let playlist = try await store.playlist(localId) { try await create(server, playlist) }
        case .remove(let localId):
            guard let playlist = try await store.playlist(localId) else { return }
            // Changed here after all: it stays, only on the phone.
            if playlist.edited {
                try await store.unlinkPlaylist(playlist.id)
            } else {
                try await store.deleteUnchangedPlaylist(playlist.id, updatedAt: playlist.updatedAt)
            }
        case .unlink(let localId):
            try await store.unlinkPlaylist(localId)
        case .deleteOnServer(let serverId):
            try await deleteOnServer(server, serverId)
        case .forgetDeletion(let serverId):
            removeDeleted(server.account, serverId)
        }
    }

    /// Makes a phone copy of a server playlist. A copy left on the phone by an earlier sign-in,
    /// with the same name and songs, is linked again instead of made twice.
    private func importPlaylist(_ server: ServerInUse, _ serverId: String, _ stamp: String) async throws {
        let store = library.store
        let full = try await server.client.fullPlaylist(serverId)
        guard let rows = await rowsOf(full) else { return }
        let isFamily = isFamilyPlaylist(owner: full.owner, username: server.username)
        let songs = syncedSongsText(full.entry.map(\.id))
        for playlist in try await store.phonePlaylists() where playlist.name == full.name {
            if try await store.playlistItems(playlist.id).map(\.trackId) == rows.map(\.trackId) {
                try await store.linkPlaylist(playlist.id, serverId: serverId, sourceId: server.sourceId, syncedAt: playlist.updatedAt,
                                             name: playlist.name, stamp: stamp)
                try await store.markFamily(playlist.id, family: isFamily)
                try await store.markFamilyBase(playlist.id, songs: songs)
                return
            }
        }
        let now = epochNowMs()
        let at = min(epochMs(full.changed) ?? now, now)
        let id = UUID().uuidString.lowercased()
        let row = PlaylistRow(id: id, name: full.name, createdAt: min(epochMs(full.created) ?? at, at), updatedAt: at,
                              serverId: serverId, sourceId: server.sourceId, syncedAt: at, syncedName: full.name, serverStamp: stamp,
                              family: isFamily, syncedSongs: songs)
        try await store.insertServerPlaylist(row, items: await items(id, rows))
    }

    /// Takes the server's songs into the phone's copy, keeping songs only on the phone where they
    /// were. Skipped if the playlist is edited meanwhile; the next sync settles it.
    private func pull(_ server: ServerInUse, _ localId: String, _ serverId: String, _ stamp: String, _ keepName: Bool) async throws {
        let store = library.store
        guard let before = try await store.playlist(localId) else { return }
        let full = try await server.client.fullPlaylist(serverId)
        guard let fromServer = await rowsOf(full) else { return }
        let localItems = try await store.playlistItems(before.id)
        let local = localItems.map { PlaylistSyncRow($0.trackId, $0.serverSongId) }
        let copies = try await serverCopies(server.sourceId, local.map(\.trackId))
        let rows = withPhoneOnly(fromServer, local) { serverSongFor($0, copies[$0.trackId] ?? []) == nil }
        var updated = before
        updated.syncedSongs = syncedSongsText(full.entry.map(\.id))
        if keepName {
            // Still counts as changed here until the name reaches the server.
            updated.serverStamp = stamp
        } else {
            let at = max(epochNowMs(), before.updatedAt + 1)
            updated.name = full.name
            updated.updatedAt = at
            updated.syncedAt = at
            updated.syncedName = full.name
            updated.serverStamp = stamp
        }
        let ids = await items(before.id, rows, ids: before.family ? keptRowIds(localItems.map { (id: $0.id, trackId: $0.trackId) }, rows) : [])
        guard try await store.takeServerSongs(expected: before, items: ids, updated: updated) else { return }
        if keepName {
            try await server.client.updatePlaylist(serverId, name: before.name)
            guard let after = try await listingStamp(server, serverId) else { return }
            try await store.markSynced(before.id, syncedAt: before.updatedAt, name: before.name, stamp: after)
        }
    }

    /// Sends the phone's songs and name. When the server changed too since both last agreed, it is
    /// settled like a sync would instead.
    private func push(_ server: ServerInUse, _ playlist: PlaylistRow) async throws {
        guard let serverId = playlist.serverId, let linked = playlist.linked else { return }
        // A family playlist is never replaced whole: its own changes go on top.
        if playlist.family {
            try await pushFamily(server, playlist, serverId)
            return
        }
        let client = server.client
        let listed = try await client.fullPlaylists().first { $0.id == serverId }
            .map { ServerPlaylist($0.id, $0.name, stampOf($0.changed, $0.songCount)) }
        guard let listed, listed.stamp == playlist.serverStamp else {
            if let step = planLinked(listed, linked) { try await run(server, step) }
            return
        }
        let there = try await client.fullPlaylist(serverId)
        let songs = try await songsToSend(server, playlist.id)
        var sent = false
        if there.entry.map(\.id) != songs {
            try await client.replacePlaylistSongs(serverId, songIds: songs, formPost: server.formPost)
            sent = true
        }
        if there.name != playlist.name {
            try await client.updatePlaylist(serverId, name: playlist.name)
            sent = true
        }
        let after: String
        if sent {
            guard let stamp = try await listingStamp(server, serverId) else { return }
            after = stamp
        } else {
            after = listed.stamp
        }
        try await library.store.markSynced(playlist.id, syncedAt: playlist.updatedAt, name: playlist.name, stamp: after)
    }

    /// Sends a family playlist's own changes since both sides last agreed, on top of everyone
    /// else's (`familySend` decides what): songs taken out here by their place in the server's
    /// list now, songs added here at the end, then its name. The phone then takes the server's
    /// list and name; a song whose removal waits stays out of the phone's copy, and the copy stays
    /// marked changed so it is sent again.
    private func pushFamily(_ server: ServerInUse, _ playlist: PlaylistRow, _ serverId: String) async throws {
        let client = server.client
        let store = library.store
        let there = try await client.fullPlaylist(serverId)
        let current = there.entry.map(\.id)
        let phone = try await familySongsToSend(server, playlist.id)
        let base = syncedSongsOf(playlist.syncedSongs) ?? phone
        let removing = !familyChanges(base: base, phone: phone, current: current).removeAt.isEmpty
        let cleanOnly: Bool? = removing ? await familyPlan()?.abilities.cleanOnly : false
        var listedCount: Int?
        if removing && cleanOnly == false {
            listedCount = try await client.fullPlaylists().first { $0.id == serverId }?.songCount
        }
        let send = familySend(base: base, phone: phone, current: current, listedCount: listedCount, cleanOnly: cleanOnly)
        if !send.changes.none {
            try await client.updatePlaylist(serverId, songIdsToAdd: send.changes.add, songIndexesToRemove: send.changes.removeAt,
                                            formPost: server.formPost)
            // Kept before anything else can fail, so nothing is sent twice.
            try await store.markFamilyBase(playlist.id, songs: syncedSongsText(send.baseAfterSend))
        }
        if playlist.name != playlist.syncedName && playlist.name != there.name {
            do {
                try await client.updatePlaylist(serverId, name: playlist.name)
            } catch let OctoError.server(code, _) where code == 50 {
                // Only its maker or a family manager may rename it; the server's name comes back below.
            }
        }
        guard let stamp = try await listingStamp(server, serverId) else { return }
        let full = try await client.fullPlaylist(serverId)
        guard let fromServer = await rowsOf(full) else { return }
        let localItems = try await store.playlistItems(playlist.id)
        let local = localItems.map { PlaylistSyncRow($0.trackId, $0.serverSongId) }
        let copies = try await serverCopies(server.sourceId, local.map(\.trackId))
        let rows = withoutHeld(withPhoneOnly(fromServer, local) { serverSongFor($0, copies[$0.trackId] ?? []) == nil }, send.held)
        var updated = playlist
        let at = max(epochNowMs(), playlist.updatedAt + 1)
        updated.name = full.name
        updated.updatedAt = at
        // Still changed here while a removal waits, so it is sent again.
        updated.syncedAt = send.held.isEmpty ? at : playlist.syncedAt
        updated.syncedName = full.name
        updated.serverStamp = stamp
        updated.syncedSongs = syncedSongsText(full.entry.map(\.id))
        _ = try await store.takeServerSongs(expected: playlist, items: await items(playlist.id, rows, ids: keptRowIds(localItems.map { (id: $0.id, trackId: $0.trackId) }, rows)), updated: updated)
    }

    /// Makes a phone playlist on the server and links the two.
    private func create(_ server: ServerInUse, _ playlist: PlaylistRow) async throws {
        guard playlist.serverId == nil, playlist.sourceId == server.sourceId else { return }
        let songs = try await songsToSend(server, playlist.id)
        let made = try await server.client.makePlaylist(name: playlist.name, songIds: songs, formPost: server.formPost)
        let made2 = made?.id
        let serverId: String
        if let made2 { serverId = made2 } else if let named = try await newestNamed(server, playlist.name) { serverId = named } else { return }
        // Linked at once, so a failure from here on can never make it twice.
        let first = made.map { stampOf($0.changed, $0.songCount) } ?? ""
        let store = library.store
        try await store.linkPlaylist(playlist.id, serverId: serverId, sourceId: server.sourceId, syncedAt: playlist.updatedAt,
                                     name: playlist.name, stamp: first)
        if let stamp = try await listingStamp(server, serverId) {
            try await store.markSynced(playlist.id, syncedAt: playlist.updatedAt, name: playlist.name, stamp: stamp)
        }
    }

    private func deleteOnServer(_ server: ServerInUse, _ serverId: String) async throws {
        do {
            try await server.client.deletePlaylist(serverId)
        } catch OctoError.notFound {
            // Already gone.
        } catch let OctoError.server(code, _) where code == 50 {
            // Not allowed (a family playlist for anyone but a manager): never tried again; it comes back to the phone with the next sync.
        }
        removeDeleted(server.account, serverId)
    }

    /// The server songs a phone playlist goes to the server as, in order, without the songs only
    /// on the phone.
    private func songsToSend(_ server: ServerInUse, _ localId: String) async throws -> [String] {
        let rows = try await library.store.playlistItems(localId).map { PlaylistSyncRow($0.trackId, $0.serverSongId) }
        let copies = try await serverCopies(server.sourceId, rows.map(\.trackId))
        return rows.compactMap { serverSongFor($0, copies[$0.trackId] ?? []) }
    }

    /// A family playlist's songs as the server knows them: each song from the server's copy by the
    /// id the server gave it, a song added here by its copy on the server; songs only on the phone
    /// left out.
    private func familySongsToSend(_ server: ServerInUse, _ localId: String) async throws -> [String] {
        let rows = try await library.store.playlistItems(localId).map { PlaylistSyncRow($0.trackId, $0.serverSongId) }
        let copies = try await serverCopies(server.sourceId, rows.map(\.trackId))
        return rows.compactMap { $0.serverSongId ?? serverSongFor($0, copies[$0.trackId] ?? []) }
    }

    /// Each library song's copies on this server, by song.
    private func serverCopies(_ sourceId: String, _ trackIds: [String]) async throws -> [String: [String]] {
        let all = try await library.store.copies(of: distinct(trackIds.filter { !isFind($0) }))
        var copies: [String: [String]] = [:]
        for copy in all where copy.sourceId == sourceId { copies[copy.mergedId, default: []].append(copy.nativeId) }
        return copies
    }

    /// A server playlist's songs as phone rows, or nil when they could not all be placed (the
    /// server was signed out meanwhile).
    private func rowsOf(_ playlist: ServerPlaylistInfo) async -> [PlaylistSyncRow]? {
        let ids = await discovery.idsFromServer(playlist.entry)
        guard ids.count == playlist.entry.count else { return nil }
        return serverRows(playlist.entry.map(\.id), ids)
    }

    /// The rows of a playlist's songs, each keeping the id given for its place (0 for a new one).
    private func items(_ playlistId: String, _ rows: [PlaylistSyncRow], ids: [Int64?] = []) async -> [PlaylistItem] {
        var keys: [String: String] = [:]
        for track in await library.tracks(ids: rows.map(\.trackId).filter { !isFind($0) }) { keys[track.id] = track.relinkKey }
        return rows.enumerated().map { position, row in
            PlaylistItem(id: ids.indices.contains(position) ? (ids[position] ?? 0) : 0,
                         playlistId: playlistId, trackId: row.trackId, relinkKey: keys[row.trackId] ?? "", position: position,
                         serverSongId: row.serverSongId)
        }
    }

    /// The playlist's stamp as the server lists it now, or nil when the server no longer lists it.
    private func listingStamp(_ server: ServerInUse, _ serverId: String) async throws -> String? {
        try await server.client.fullPlaylists().first { $0.id == serverId }.map { stampOf($0.changed, $0.songCount) }
    }

    /// For a server too old to answer with the playlist it made: the newest one of the user's by
    /// that name that no phone playlist is linked to (never a family playlist).
    private func newestNamed(_ server: ServerInUse, _ name: String) async throws -> String? {
        let linked = Set(try await library.store.serverPlaylists(server.sourceId).compactMap(\.serverId))
        return newestNamedCandidates(try await server.client.fullPlaylists(), name: name, username: server.username, linked: linked)
            .max { ($0.created ?? "") < ($1.created ?? "") }?.id
    }

    /// The signed-in server, if there is one. Playlists linked for another account on the same
    /// address are let go first; other servers' links stay for when they are in use again.
    private func signedIn() async -> ServerInUse? {
        guard let server = session.inUse else { return nil }
        let before = prefs.string("linked_by", server.sourceId)
        if before != server.account {
            prefs.set(server.account, "linked_by", server.sourceId)
            if before != nil { try? await library.store.unlinkServer(server.sourceId) }
        }
        return server
    }

    private func deleted(_ account: String) -> Set<String> { prefs.strings("deleted", account) }

    private func addDeleted(_ account: String, _ serverId: String) {
        prefs.set(strings: deleted(account).union([serverId]), "deleted", account)
    }

    private func removeDeleted(_ account: String, _ serverId: String) {
        prefs.set(strings: deleted(account).subtracting([serverId]), "deleted", account)
    }
}

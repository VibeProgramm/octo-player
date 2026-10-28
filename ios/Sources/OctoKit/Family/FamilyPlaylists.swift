import Foundation

/// The owner Octo lists every family playlist under.
public let familyPlaylistOwner = "Family"

/// The extension a server lists when its family playlist rows say who may rename or delete them
/// and it takes removals by song (octoSongToRemove).
public let familyPlaylistsExtension = "octoFamilyPlaylists"

/// Whether a playlist is one of the family's. A server that marks its family playlists
/// (`octoFamily`) decides; the owner name is read only from older servers: listed under the
/// family, and not under the signed-in account itself.
public func isFamilyPlaylist(owner: String?, username: String?, octoFamily: Bool? = nil) -> Bool {
    if let octoFamily { return octoFamily }
    let name = (owner ?? "").trimmingCharacters(in: .whitespaces)
    let user = (username ?? "").trimmingCharacters(in: .whitespaces)
    return name.caseInsensitiveCompare(familyPlaylistOwner) == .orderedSame && name.caseInsensitiveCompare(user) != .orderedSame
}

/// What the signed-in listener may do with a playlist. `rename` is sure to work; `mayRename` is
/// for the server to decide (a family playlist's maker may, and the app cannot tell who made it).
public struct PlaylistAccess: Equatable, Sendable {
    public var add = false
    public var remove = false
    public var reorder = false
    public var rename = false
    public var mayRename = false
    public var describe = false
    public var visibility = false
    public var delete = false
    public var family = false

    public init(add: Bool = false, remove: Bool = false, reorder: Bool = false, rename: Bool = false, mayRename: Bool = false,
                describe: Bool = false, visibility: Bool = false, delete: Bool = false, family: Bool = false) {
        self.add = add
        self.remove = remove
        self.reorder = reorder
        self.rename = rename
        self.mayRename = mayRename
        self.describe = describe
        self.visibility = visibility
        self.delete = delete
        self.family = family
    }
}

/// The listener's own playlist: everything. A family playlist the server lists as changeable: add
/// songs; Octo's own answers (`mayRename`, `mayDelete`) decide rename and delete where the server
/// sends them; a server that removes by song (`removesById`) lets every editor take songs out; on
/// older servers a listener who hears only clean songs, or one whose family details are not known
/// yet, takes nothing out (the server hides explicit songs from them, so places in their list are
/// not the playlist's), and rename and delete go to a family manager, with rename left to the
/// server for anyone else; never reorder, describe, or make public or private (Octo refuses or
/// ignores those). Any other playlist: nothing. A playlist with no owner named, or a listener the
/// server did not name, counts as the listener's own.
public func playlistAccess(owner: String?, readonly: Bool, username: String?, me: FamilyMe?, octoFamily: Bool? = nil,
                           mayRename: Bool? = nil, mayDelete: Bool? = nil, removesById: Bool = false) -> PlaylistAccess {
    let family = isFamilyPlaylist(owner: owner, username: username, octoFamily: octoFamily)
    if readonly { return PlaylistAccess(family: family) }
    if family {
        let manager = me?.abilities.manageFamily == true
        return PlaylistAccess(add: true, remove: removesById || me?.abilities.cleanOnly == false, rename: mayRename ?? manager,
                              mayRename: mayRename == nil && !manager && me?.abilities.familyPlaylistsEdit != false,
                              delete: mayDelete ?? manager, family: true)
    }
    let ownerName = (owner ?? "").trimmingCharacters(in: .whitespaces)
    let user = (username ?? "").trimmingCharacters(in: .whitespaces)
    let own = ownerName.isEmpty || user.isEmpty || ownerName.caseInsensitiveCompare(user) == .orderedSame
    return own ? PlaylistAccess(add: true, remove: true, reorder: true, rename: true, describe: true, visibility: true, delete: true)
        : PlaylistAccess()
}

extension ServerPlaylistInfo {
    /// A playlist row's access for the signed-in listener.
    public func access(username: String?, me: FamilyMe?, removesById: Bool) -> PlaylistAccess {
        playlistAccess(owner: owner, readonly: readonly, username: username, me: me, octoFamily: octoFamily,
                       mayRename: octoFamilyMayRename, mayDelete: octoFamilyMayDelete, removesById: removesById)
    }
}

/// A song to take out of a family playlist by its id and which copy of that id it is (1 for
/// the first), as Octo's octoSongToRemove takes it.
public struct SongToRemove: Equatable, Sendable {
    public var id: String
    public var copy: Int
    public init(id: String, copy: Int) { self.id = id; self.copy = copy }
}

/// The songs at these places as Octo takes them out by song, in place order.
public func songsToRemove(_ ids: [String], positions: [Int]) -> [SongToRemove] {
    Array(Set(positions)).sorted().filter { ids.indices.contains($0) }.map { at in
        SongToRemove(id: ids[at], copy: ids[0...at].filter { $0 == ids[at] }.count)
    }
}

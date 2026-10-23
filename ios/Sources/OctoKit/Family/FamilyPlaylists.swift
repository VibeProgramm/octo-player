import Foundation

/// The owner Octo lists every family playlist under.
public let familyPlaylistOwner = "Family"

/// Whether a playlist is one of the family's: listed under the family, and not under the
/// signed-in account itself.
public func isFamilyPlaylist(owner: String?, username: String?) -> Bool {
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
/// songs, and take them out unless the listener hears only clean songs (the server hides explicit
/// ones from them, so places in their list are not the playlist's); rename and delete for a family
/// manager, rename for the server to decide otherwise; never reorder, describe, or make public or
/// private (Octo refuses or ignores those). Any other playlist: nothing. A playlist with no owner
/// named, or a listener the server did not name, counts as the listener's own.
public func playlistAccess(owner: String?, readonly: Bool, username: String?, me: FamilyMe?) -> PlaylistAccess {
    let family = isFamilyPlaylist(owner: owner, username: username)
    if readonly { return PlaylistAccess(family: family) }
    if family {
        let manager = me?.abilities.manageFamily == true
        return PlaylistAccess(add: true, remove: me?.abilities.cleanOnly != true, rename: manager,
                              mayRename: !manager && me?.abilities.familyPlaylistsEdit != false, delete: manager, family: true)
    }
    let ownerName = (owner ?? "").trimmingCharacters(in: .whitespaces)
    let user = (username ?? "").trimmingCharacters(in: .whitespaces)
    let own = ownerName.isEmpty || user.isEmpty || ownerName.caseInsensitiveCompare(user) == .orderedSame
    return own ? PlaylistAccess(add: true, remove: true, reorder: true, rename: true, describe: true, visibility: true, delete: true)
        : PlaylistAccess()
}

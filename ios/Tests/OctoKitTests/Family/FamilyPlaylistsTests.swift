import Foundation
import Testing
@testable import OctoKit

@Suite struct FamilyPlaylistsTests {
    private func me(manager: Bool = false, cleanOnly: Bool = false, edit: Bool = true) -> FamilyMe {
        var me = FamilyMe()
        me.abilities.manageFamily = manager
        me.abilities.cleanOnly = cleanOnly
        me.abilities.familyPlaylistsEdit = edit
        return me
    }

    private let own = PlaylistAccess(add: true, remove: true, reorder: true, rename: true, describe: true, visibility: true, delete: true)
    private let member = PlaylistAccess(add: true, remove: true, mayRename: true, family: true)
    private let manager = PlaylistAccess(add: true, remove: true, rename: true, delete: true, family: true)

    @Test func theFamilyOwnerIsRecognizedBesideTheSignedInAccount() {
        #expect(isFamilyPlaylist(owner: "Family", username: "alex"))
        #expect(isFamilyPlaylist(owner: "family", username: "alex"))
        #expect(isFamilyPlaylist(owner: " Family ", username: "alex"))
        #expect(!isFamilyPlaylist(owner: "Family", username: "Family"))
        #expect(!isFamilyPlaylist(owner: "alex", username: "alex"))
        #expect(!isFamilyPlaylist(owner: nil, username: "alex"))
    }

    @Test func aListenersOwnPlaylistAllowsEverything() {
        #expect(playlistAccess(owner: "alex", readonly: false, username: "alex", me: me()) == own)
        #expect(playlistAccess(owner: "Alex", readonly: false, username: "alex", me: me()) == own)
        #expect(playlistAccess(owner: nil, readonly: false, username: "alex", me: me()) == own)
    }

    @Test func someoneElsesPlaylistAllowsNothing() {
        #expect(playlistAccess(owner: "sam", readonly: false, username: "alex", me: me()) == PlaylistAccess())
    }

    @Test func aFamilyMemberMayAddRemoveAndAskToRename() {
        #expect(playlistAccess(owner: "Family", readonly: false, username: "alex", me: me()) == member)
        #expect(playlistAccess(owner: "Family", readonly: false, username: "alex", me: nil) == member)
    }

    @Test func aFamilyManagerMayRenameAndDelete() {
        #expect(playlistAccess(owner: "Family", readonly: false, username: "alex", me: me(manager: true)) == manager)
    }

    @Test func aCleanOnlyListenerCannotTakeSongsOut() {
        let access = playlistAccess(owner: "Family", readonly: false, username: "alex", me: me(cleanOnly: true))
        #expect(access.add)
        #expect(!access.remove)
    }

    @Test func aReadOnlyPlaylistAllowsNothingButSaysFamily() {
        #expect(playlistAccess(owner: "Family", readonly: true, username: "alex", me: me()) == PlaylistAccess(family: true))
    }

    @Test func aBlankUsernameStillSeparatesOwnFromFamily() {
        #expect(playlistAccess(owner: "alex", readonly: false, username: "", me: me()) == own)
        #expect(playlistAccess(owner: "Family", readonly: false, username: "", me: me()) == member)
    }
}

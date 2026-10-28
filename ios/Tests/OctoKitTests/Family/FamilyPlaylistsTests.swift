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
        let unknown = playlistAccess(owner: "Family", readonly: false, username: "alex", me: nil)
        #expect(unknown.add)
        #expect(!unknown.remove)
        #expect(unknown.mayRename)
    }

    @Test func aServerThatRemovesBySongLetsEveryEditorTakeSongsOut() {
        #expect(playlistAccess(owner: "Family", readonly: false, username: "alex", me: nil, removesById: true).remove)
        #expect(playlistAccess(owner: "Family", readonly: false, username: "alex", me: me(cleanOnly: true), removesById: true).remove)
        #expect(!playlistAccess(owner: "Family", readonly: false, username: "alex", me: me(cleanOnly: true)).remove)
    }

    @Test func theServersOwnAnswersDecideRenameAndDelete() {
        let given = playlistAccess(owner: "Family", readonly: false, username: "alex", me: me(), mayRename: true, mayDelete: false)
        #expect(given.rename)
        #expect(!given.mayRename)
        #expect(!given.delete)
        let refused = playlistAccess(owner: "Family", readonly: false, username: "alex", me: me(manager: true), mayRename: false)
        #expect(!refused.rename)
    }

    @Test func theServersFamilyMarkOverridesTheOwnerName() {
        #expect(!isFamilyPlaylist(owner: "Family", username: "alex", octoFamily: false))
        #expect(isFamilyPlaylist(owner: "sam", username: "alex", octoFamily: true))
        #expect(!playlistAccess(owner: "Family", readonly: false, username: "alex", me: me(), octoFamily: false).family)
        #expect(playlistAccess(owner: "sam", readonly: false, username: "alex", me: me(), octoFamily: true).family)
    }

    @Test func aPlaylistRowPassesItsOwnFields() {
        let row = ServerPlaylistInfo(id: "p", owner: "sam", octoFamily: true, octoFamilyMayRename: true, octoFamilyMayDelete: false)
        let access = row.access(username: "alex", me: me(), removesById: true)
        #expect(access == PlaylistAccess(add: true, remove: true, rename: true, delete: false, family: true))
    }

    @Test func songsAreTakenOutByIdAndCopy() {
        #expect(songsToRemove(["a", "b", "a", "c"], positions: [2, 0, 2, 9])
            == [SongToRemove(id: "a", copy: 1), SongToRemove(id: "a", copy: 2)])
    }

    @Test func theUpdateCallCarriesSongsToRemove() {
        let calls = updateCalls("p", name: nil, comment: nil, isPublic: nil, songIdsToAdd: [], songIndexesToRemove: [],
                                songsToRemove: [SongToRemove(id: "s1", copy: 2)], formPost: false)
        #expect(calls[0].params.contains(.init("octoSongToRemove", "s1@2")))
    }

    @Test func familyFieldsReadAsBooleansOrText() throws {
        let json = #"{"id":"p","octoFamily":"true","octoFamilyCreatedBy":"sam","octoFamilyMayRename":false,"octoFamilyMayDelete":"false"}"#
        let row = try JSONDecoder().decode(ServerPlaylistInfo.self, from: Data(json.utf8))
        #expect(row.octoFamily == true)
        #expect(row.octoFamilyCreatedBy == "sam")
        #expect(row.octoFamilyMayRename == false)
        #expect(row.octoFamilyMayDelete == false)
        let plain = try JSONDecoder().decode(ServerPlaylistInfo.self, from: Data(#"{"id":"p"}"#.utf8))
        #expect(plain.octoFamily == nil)
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

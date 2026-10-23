import Foundation
import Testing
@testable import OctoKit

@Suite struct PlaylistDetailsTests {
    @Test func songsAndLengthComeFirst() {
        #expect(playlistDetails(songs: 12, length: "45:10", kept: nil, phoneOnly: 0, onServer: false) == "12 songs · 45:10")
    }

    @Test func anEmptyPlaylistSaysOnlyItsCount() {
        #expect(playlistDetails(songs: 0, length: "0:00", kept: nil, phoneOnly: 0, onServer: true) == "0 songs")
    }

    @Test func keptDownloadsAndPhoneOnlySongsJoinTheLine() {
        #expect(playlistDetails(songs: 12, length: "45:10", kept: "3 of 9 downloaded", phoneOnly: 2, onServer: true)
            == "12 songs · 45:10 · 3 of 9 downloaded · 2 songs only on this phone")
        #expect(playlistDetails(songs: 3, length: "9:00", kept: nil, phoneOnly: 1, onServer: true)
            == "3 songs · 9:00 · 1 song only on this phone")
    }

    @Test func aPlaylistOnlyOnThePhoneNeverSaysSo() {
        #expect(playlistDetails(songs: 3, length: "9:00", kept: nil, phoneOnly: 3, onServer: false) == "3 songs · 9:00")
    }
}

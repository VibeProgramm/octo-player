@testable import OctoKit
import Testing

struct SwipeActionTests {
    @Test func storedNamesMatchTheOtherApps() {
        #expect(SwipeAction.allCases.map(\.storedName) == ["PlayNext", "AddToQueue", "Off"])
    }

    @Test func anUnknownNameReadsAsPlayNext() {
        #expect(SwipeAction.stored("Nothing", or: .playNext) == .playNext)
        #expect(SwipeAction.stored(nil, or: .playNext) == .playNext)
        #expect(SwipeAction.stored("Off", or: .playNext) == .off)
    }

    @Test func eachChoiceSaysWhatItDoes() {
        #expect(SwipeAction.playNext.label == "Play next")
        #expect(SwipeAction.addToQueue.label == "Add to queue")
        #expect(SwipeAction.off.label == "Off")
    }

    @Test func theSwipeAndAutoplayShareTheQueueGroup() {
        #expect(SettingsIndex.all.filter { $0.id == "swipe_action" }.count == 1)
        #expect(SettingsIndex.songSwipe.section == "Queue")
        #expect(SettingsIndex.autoplay.section == "Queue")
    }

    @Test func undoFindsTheListenersSongNotAutoplays() {
        // a, b, c with a on, then Autoplay's x and y; the listener adds x.
        let at = ownSongsAt(5, upcoming: [1, 2, 3, 4], autoplay: [false, false, false, true, true])
        #expect(at == 3)
        #expect(findInserted(["a", "b", "c", "x", "x", "y"], ["x"], insertedAt: at) == 3)
    }
}

@testable import OctoKit
import Testing

/// The message for songs put in the queue says how many, and where.
struct QueueAddWordsTests {
    @Test func oneSong() {
        #expect(queueAddWords(count: 1, next: true) == "Playing next")
        #expect(queueAddWords(count: 1, next: false) == "Added to queue")
    }

    @Test func severalSongs() {
        #expect(queueAddWords(count: 12, next: true) == "12 songs playing next")
        #expect(queueAddWords(count: 12, next: false) == "12 songs added to queue")
    }
}

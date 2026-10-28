import Foundation
import Testing
@testable import OctoKit

private actor Signal {
    private var fired = false
    private var waiting: [CheckedContinuation<Void, Never>] = []

    func fire() {
        fired = true
        waiting.forEach { $0.resume() }
        waiting = []
    }

    func wait() async {
        if fired { return }
        await withCheckedContinuation { waiting.append($0) }
    }
}

private final class Journal: @unchecked Sendable {
    private let lock = NSLock()
    private var lines: [String] = []

    func add(_ line: String) {
        lock.lock()
        lines.append(line)
        lock.unlock()
    }

    var all: [String] {
        lock.lock()
        defer { lock.unlock() }
        return lines
    }
}

private let stillLoading = OctoError.server(code: 0, message: "Still loading your other countries. Try again in a moment.")

@Suite struct CountryReadsTests {
    @Test func aStillLoadingRefusalIsAskedAgainWhenAReadFinishes() async throws {
        let reads = CountryReads()
        let journal = Journal()
        let slowStarted = Signal()
        let release = Signal()
        let refused = Signal()

        let slow = Task {
            try await reads.read(country: "fr") {
                journal.add("slow started")
                await slowStarted.fire()
                await release.wait()
                journal.add("slow finished")
                return 1
            }
        }
        await slowStarted.wait()

        let asked = Task {
            try await reads.read(country: "de") {
                journal.add("asked")
                if journal.all.filter({ $0 == "asked" }).count == 1 {
                    await refused.fire()
                    throw stillLoading
                }
                return 2
            }
        }
        await refused.wait()
        await release.fire()

        #expect(try await asked.value == 2)
        #expect(try await slow.value == 1)
        #expect(journal.all == ["slow started", "asked", "slow finished", "asked"])
    }

    @Test func aRefusalWithNothingLoadingIsAskedOnceMoreThenShown() async {
        let reads = CountryReads()
        let journal = Journal()
        await #expect(throws: stillLoading) {
            try await reads.read(country: "de") {
                journal.add("asked")
                throw stillLoading
            }
        }
        #expect(journal.all == ["asked", "asked"])
    }

    @Test func theListenersOwnCountryIsNeverAskedAgain() async {
        let reads = CountryReads()
        let journal = Journal()
        await #expect(throws: stillLoading) {
            try await reads.read(country: nil) {
                journal.add("asked")
                throw stillLoading
            }
        }
        #expect(journal.all == ["asked"])
    }

    @Test func anotherRefusalIsShownAtOnce() async {
        let reads = CountryReads()
        let journal = Journal()
        let refusal = OctoError.server(code: 10, message: "You can pin at most 3 countries.")
        await #expect(throws: refusal) {
            try await reads.read(country: "de") {
                journal.add("asked")
                throw refusal
            }
        }
        #expect(journal.all == ["asked"])
    }
}

import Foundation

/// The reads of other countries' charts this app has on their way. A server still loading the
/// countries already asked for refuses one more (Subsonic code 0). That read waits for another to
/// finish, which is what frees the server, and asks again, repeating while the count keeps
/// dropping. With none other on its way it asks once more right away, then gives the answer.
public actor CountryReads {
    private var onTheWay = 0
    private var waiters: [UUID: (below: Int, continuation: CheckedContinuation<Void, Never>)] = [:]

    public init() {}

    /// Runs `call`, a read of `country`'s charts. A nil country is the listener's own: not
    /// counted, never asked again.
    public func read<T: Sendable>(country: String?, _ call: @Sendable () async throws -> T) async throws -> T {
        guard country != nil else { return try await call() }
        onTheWay += 1
        defer { finish() }
        var alone = false
        while true {
            do {
                return try await call()
            } catch OctoError.server(let code, let message) where code == 0 {
                let before = onTheWay
                if before <= 1 {
                    if alone { throw OctoError.server(code: code, message: message) }
                    alone = true
                } else {
                    try await waitUntilFewerThan(before)
                }
            }
        }
    }

    private func finish() {
        onTheWay -= 1
        for (id, waiter) in waiters where onTheWay < waiter.below {
            waiters[id] = nil
            waiter.continuation.resume()
        }
    }

    private func waitUntilFewerThan(_ count: Int) async throws {
        let id = UUID()
        await withTaskCancellationHandler {
            await withCheckedContinuation { continuation in
                if onTheWay < count || Task.isCancelled {
                    continuation.resume()
                } else {
                    waiters[id] = (count, continuation)
                }
            }
        } onCancel: {
            Task { await self.stopWaiting(id) }
        }
        try Task.checkCancellation()
    }

    private func stopWaiting(_ id: UUID) {
        waiters.removeValue(forKey: id)?.continuation.resume()
    }
}

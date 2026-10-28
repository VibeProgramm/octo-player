import Foundation

/// The place a message pill has just left, which keeps taking taps for a moment, so a tap aimed
/// at the pill as it goes never reaches what is under it.
public struct LateTapGuard: Sendable {
    /// How long the place keeps taking taps.
    public static let seconds: TimeInterval = 0.6
    public private(set) var frame: CGRect?
    public private(set) var until = Date.distantPast

    public init() {}

    /// The pill has left `frame`.
    public mutating func start(at frame: CGRect, now: Date = Date()) {
        self.frame = frame
        until = now.addingTimeInterval(Self.seconds)
    }

    /// Whether a tap at `point` is taken.
    public func takes(_ point: CGPoint, now: Date = Date()) -> Bool {
        guard let frame, now < until else { return false }
        return frame.contains(point)
    }
}

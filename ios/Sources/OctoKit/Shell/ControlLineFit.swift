import Foundation

/// One piece of a page's control line that may move into More: its width on the line, and the
/// order it moves in, lowest first (Sort before Search before a range).
public struct LinePiece: Sendable, Equatable {
    public var id: String
    public var width: Double
    public var priority: Int

    public init(_ id: String, width: Double, priority: Int) {
        self.id = id
        self.width = width
        self.priority = priority
    }
}

/// How a control line is laid out at a width: which pieces moved into More, whether More shows,
/// and what became of the segments (the page's main choice), which never move.
public struct LineFit: Sendable, Equatable {
    /// The pieces in More, in the order they moved.
    public var moved: [String]
    public var showsMore: Bool
    /// The segments' labels give way to fit.
    public var shrinks: Bool
    /// Even shrunk the segments do not fit, so they are one menu button.
    public var segmentsAsMenu: Bool

    public init(moved: [String] = [], showsMore: Bool = false, shrinks: Bool = false, segmentsAsMenu: Bool = false) {
        self.moved = moved
        self.showsMore = showsMore
        self.shrinks = shrinks
        self.segmentsAsMenu = segmentsAsMenu
    }
}

/// How far segment labels may give way before the segments become a menu.
public let segmentsLeastShare = 0.75

/// What fits on a control line `width` across. `fixed` is what never moves (the page's own
/// lead, Play and Shuffle), `segments` the main choice's natural width (nil without one),
/// `pieces` what may move into More, `ownMore` whether the page has More rows of its own, and
/// every item is `spacing` from the next. Pieces move into More, lowest priority first, until the
/// rest fits; only then do the segments' labels give way, to `segmentsLeastShare` of their
/// width, and past that the segments are a menu button. More, `moreWidth` across, shows
/// while it holds anything.
public func fitControlLine(width: Double, fixed: Double, segments: Double?, pieces: [LinePiece], ownMore: Bool,
                           moreWidth: Double = 48, spacing: Double = 8) -> LineFit {
    let order = pieces.enumerated().sorted { a, b in
        a.element.priority != b.element.priority ? a.element.priority < b.element.priority : a.offset < b.offset
    }.map(\.element)
    var moved: [String] = []
    func need(segmentsWidth: Double?) -> Double {
        let shown = pieces.filter { !moved.contains($0.id) }.reduce(0) { $0 + $1.width + spacing }
        let more = ownMore || !moved.isEmpty ? moreWidth + spacing : 0
        return fixed + shown + (segmentsWidth.map { $0 + spacing } ?? 0) + more
    }
    for piece in order where need(segmentsWidth: segments) > width {
        moved.append(piece.id)
    }
    let showsMore = ownMore || !moved.isEmpty
    guard let segments, need(segmentsWidth: segments) > width else {
        return LineFit(moved: moved, showsMore: showsMore)
    }
    if need(segmentsWidth: segments * segmentsLeastShare) <= width {
        return LineFit(moved: moved, showsMore: showsMore, shrinks: true)
    }
    return LineFit(moved: moved, showsMore: showsMore, segmentsAsMenu: true)
}

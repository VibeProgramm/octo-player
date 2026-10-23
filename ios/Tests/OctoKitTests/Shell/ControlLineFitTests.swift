import Foundation
import Testing
@testable import OctoKit

/// The line's room on a phone: its width less 20 at the left and 13 at the right.
private func room(_ screen: Double) -> Double { screen - 33 }

@Suite struct ControlLineFitTests {
    /// Favorites: Sort, the three segments, then Play, Shuffle and More with Keep downloaded.
    private func favorites(_ screen: Double) -> LineFit {
        fitControlLine(width: room(screen), fixed: 96, segments: 200, pieces: [LinePiece("sort", width: 112, priority: 1)], ownMore: true)
    }

    @Test func favoritesMovesSortFirstThenShrinksTheSegmentsThenMakesThemAMenu() {
        #expect(favorites(430) == LineFit(moved: ["sort"], showsMore: true))
        #expect(favorites(393) == LineFit(moved: ["sort"], showsMore: true))
        #expect(favorites(375) == LineFit(moved: ["sort"], showsMore: true, shrinks: true))
        #expect(favorites(320) == LineFit(moved: ["sort"], showsMore: true, segmentsAsMenu: true))
    }

    /// Songs: Sort, then Search, Play and Shuffle; no More of its own.
    private func songs(_ screen: Double) -> LineFit {
        fitControlLine(width: room(screen), fixed: 96, segments: nil,
                       pieces: [LinePiece("sort", width: 150, priority: 1), LinePiece("search", width: 48, priority: 2)], ownMore: false)
    }

    @Test func songsKeepsEverythingUntilTheSmallestPhone() {
        #expect(songs(430) == LineFit())
        #expect(songs(393) == LineFit())
        #expect(songs(375) == LineFit())
        // Sort moves, and More shows for it though the page has none of its own.
        #expect(songs(320) == LineFit(moved: ["sort"], showsMore: true))
    }

    /// Live radio: the place never moves; the order goes before the radius.
    private func liveRadio(_ screen: Double) -> LineFit {
        fitControlLine(width: room(screen), fixed: 160, segments: nil,
                       pieces: [LinePiece("radius", width: 120, priority: 2), LinePiece("order", width: 110, priority: 1)], ownMore: false)
    }

    @Test func piecesMoveLowestPriorityFirstWhateverTheirPlace() {
        #expect(liveRadio(430) == LineFit(moved: ["order"], showsMore: true))
        #expect(liveRadio(393) == LineFit(moved: ["order"], showsMore: true))
        #expect(liveRadio(375) == LineFit(moved: ["order", "radius"], showsMore: true))
        #expect(liveRadio(320) == LineFit(moved: ["order", "radius"], showsMore: true))
    }

    @Test func aLineThatFitsMovesNothingAndShowsNoMore() {
        #expect(fitControlLine(width: room(320), fixed: 96, segments: 150, pieces: [], ownMore: false) == LineFit())
    }

    @Test func piecesOfOnePriorityMoveInTheirOrderOnTheLine() {
        let fit = fitControlLine(width: 100, fixed: 0, segments: nil,
                                 pieces: [LinePiece("a", width: 60, priority: 1), LinePiece("b", width: 60, priority: 1)], ownMore: false)
        #expect(fit.moved == ["a", "b"])
    }
}

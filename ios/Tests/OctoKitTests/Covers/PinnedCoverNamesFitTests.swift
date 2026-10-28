import Foundation
import Testing
@testable import OctoKit

/// A chart cover's name is set whole, with no word broken or line cut, or the cover is its
/// artwork alone.
@Suite struct PinnedCoverNamesFitTests {
    private let setter = FakeTypesetter()
    private let lists = ["Popular", "Best New Songs", "Trending Songs", "Top Hip-Hop/Rap", "Top Singer/Songwriter", "Top Children's Music",
                         "Top R&B/Soul"]
    private let sides = [300, 600, 1200]

    private func countries() -> [String] {
        let english = Locale(identifier: "en_US")
        return Locale.Region.isoRegions.compactMap { english.localizedString(forRegionCode: $0.identifier) }
    }

    private func words(_ name: String, _ side: Int) -> [CoverWords] {
        let id = "chart-\(name)"
        return coverWords(CoverSpec(id: id, name: name, line: chartCoverLine, footer: "50 songs", palette: seededPalette(id)), side: side,
                          setter: setter)
    }

    @Test func everyPinnedNameIsSetWholeOrTheCoverIsArtworkOnly() {
        let title = CoverBook.standard.layout.title
        var shownWhole = 0
        for list in lists {
            for country in countries() {
                for side in sides {
                    let name = "\(list) · \(country)"
                    let shown = words(name, side)
                    if shown.isEmpty { continue }
                    guard let set = shown.first(where: { $0.role == .title }) else {
                        Issue.record("\(name) at \(side) has no name")
                        continue
                    }
                    #expect(set.text == name)
                    #expect(!set.measured.cut, "\(name) at \(side) is cut")
                    #expect(set.measured.lines <= title.maxLines, "\(name) at \(side) has \(set.measured.lines) lines")
                    let floorSize = max(title.floorPx, Float(side) * title.floorSize).rounded(.down)
                    #expect(set.type.sizePx >= floorSize, "\(name) at \(side) is under the floor")
                    for run in unbreakableRuns(name) {
                        #expect(setter.widthOf(run, set.type) <= set.width, "\(name) at \(side) breaks '\(run)'")
                    }
                    for other in shown {
                        #expect(!other.measured.cut, "\(other.role) of \(name) at \(side) is cut")
                        #expect(other.top + other.height <= Float(side), "\(other.role) of \(name) at \(side) runs past the cover")
                    }
                    shownWhole += 1
                }
            }
        }
        #expect(shownWhole > 0)
    }

    @Test func popularJapanKeepsItsLightLineAndFooterAtSixHundred() {
        #expect(words("Popular · Japan", 600).map(\.role) == [.title, .line, .footer])
    }
}

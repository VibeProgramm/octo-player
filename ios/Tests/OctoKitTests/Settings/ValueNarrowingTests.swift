@testable import OctoKit
import Testing

/// How a settings row picks its value width: the first narrower step that takes a line off the title's side
/// with the row no taller, the wider answers still fitting on two lines.
struct ValueNarrowingTests {
    /// Discovery-like: the title side loses its second line only at 120, the narrowest width where the two
    /// widest answers still fit on two lines.
    @Test func takesTheFirstStepThatSavesALine() {
        let step = narrowValueWidth(wide: 150, ones: [150, 140, 100, 80], twos: [120, 80, 60, 50],
                                    title: { $0 <= 120 ? 40 : 60 }, value: { $0 < 150 ? 40 : 20 })
        #expect(step == 120)
    }

    @Test func keepsTheWideWidthWhenNoStepSavesALine() {
        #expect(narrowValueWidth(wide: 150, ones: [150, 140, 100], twos: [90, 80, 60], title: { _ in 60 }, value: { _ in 20 }) == nil)
    }

    @Test func neverTakesAStepThatMakesTheRowTaller() {
        let step = narrowValueWidth(wide: 150, ones: [150, 140], twos: [90, 80], title: { $0 <= 140 ? 20 : 40 },
                                    value: { $0 <= 140 ? 60 : 20 })
        #expect(step == nil)
    }

    @Test func measuresEachDistinctStepOnce() {
        var seen: [Double] = []
        _ = narrowValueWidth(wide: 150, ones: [150, 100, 100, 100], twos: [100, 90, 80, 70],
                             title: { seen.append($0); return 60 }, value: { _ in 20 })
        #expect(seen == [150, 100])
    }

    @Test func neverTriesAStepAsWideAsTheWideWidth() {
        var seen: [Double] = []
        _ = narrowValueWidth(wide: 150, ones: [150, 150], twos: [100, 90],
                             title: { seen.append($0); return 60 }, value: { _ in 20 })
        #expect(seen == [150])
    }

    /// A title side less than half a point shorter is the same height drawn.
    @Test func aHairShorterTitleSideDoesNotCount() {
        let step = narrowValueWidth(wide: 150, ones: [150, 100], twos: [90, 80],
                                    title: { $0 < 150 ? 59.7 : 60 }, value: { _ in 20 })
        #expect(step == nil)
    }

    /// 100 and 90 both save the line; the wider one wins, so the value wraps no more than it has to.
    @Test func takesTheWidestStepThatHelpsNotTheNarrowest() {
        let step = narrowValueWidth(wide: 150, ones: [150, 140, 100, 80], twos: [90, 80, 60, 50],
                                    title: { $0 <= 100 ? 40 : 60 }, value: { _ in 20 })
        #expect(step == 100)
    }

    /// A narrower answer can need more room on two lines than a wider one.
    @Test func eachStepHoldsEveryWiderAnswerOnTwoLines() {
        let step = narrowValueWidth(wide: 150, ones: [150, 140, 100], twos: [70, 130, 60],
                                    title: { $0 <= 130 ? 40 : 60 }, value: { _ in 20 })
        #expect(step == 130)
    }

    /// A row less than half a point taller is the same height drawn.
    @Test func aHairTallerRowStillCounts() {
        let step = narrowValueWidth(wide: 150, ones: [150, 100], twos: [90, 80],
                                    title: { $0 < 150 ? 40 : 60 }, value: { $0 < 150 ? 60.3 : 20 })
        #expect(step == 100)
    }
}

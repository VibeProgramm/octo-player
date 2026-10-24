@testable import OctoKit
import Testing

/// How a settings row picks its value width: the first narrower step that takes a line off the title's side
/// with the row no taller.
struct ValueNarrowingTests {
    /// Discovery-like: four answers, the title side loses its second line only once the value is as narrow as
    /// the third widest answer.
    @Test func takesTheFirstStepThatSavesALine() {
        let title = { (w: Double) in w <= 100 ? 40.0 : 60.0 }
        let step = narrowValueWidth(wide: 150, ones: [150, 140, 100, 80], twos: [90, 80, 60, 50], title: title,
                                    row: { max(title($0), $0 < 150 ? 40 : 20) })
        #expect(step == 100)
    }

    @Test func keepsTheWideWidthWhenNoStepSavesALine() {
        #expect(narrowValueWidth(wide: 150, ones: [150, 140, 100], twos: [90, 80, 60], title: { _ in 60 }, row: { _ in 60 }) == nil)
    }

    @Test func neverTakesAStepThatMakesTheRowTaller() {
        let step = narrowValueWidth(wide: 150, ones: [150, 140], twos: [90, 80], title: { $0 <= 140 ? 20 : 40 },
                                    row: { $0 <= 140 ? 60 : 40 })
        #expect(step == nil)
    }

    @Test func measuresEachDistinctStepOnce() {
        var seen: [Double] = []
        _ = narrowValueWidth(wide: 150, ones: [150, 100, 100, 100], twos: [100, 90, 80, 70],
                             title: { seen.append($0); return 60 }, row: { _ in 60 })
        #expect(seen == [150, 100])
    }
}

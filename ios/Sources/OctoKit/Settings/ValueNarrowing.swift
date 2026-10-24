import Foundation

/// The value width a settings row settles on when its value may narrow, or nil to keep `wide`. `ones` and
/// `twos` are its answers' one-line and two-line widths, widest first; `title` and `row` give the title side's
/// and the whole row's height at a value width. Each step is as wide as the next answer down, the wider ones
/// wrapping to two lines; the first step that takes a line off the title's side with the row no taller wins.
public func narrowValueWidth(wide: Double, ones: [Double], twos: [Double], title: (Double) -> Double,
                             row: (Double) -> Double) -> Double? {
    guard ones.count >= 2, twos.count == ones.count else { return nil }
    let titleAtWide = title(wide)
    let rowAtWide = row(wide)
    var last = -1.0
    for k in 1..<ones.count {
        let step = max(ones[k], twos[..<k].max() ?? 0)
        guard step < wide, step != last else { continue }
        last = step
        if title(step) < titleAtWide - 0.5, row(step) <= rowAtWide + 0.5 { return step }
    }
    return nil
}

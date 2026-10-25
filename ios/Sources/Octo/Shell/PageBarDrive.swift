#if DEBUG
import SwiftUI
import UIKit

/// Drive steps for the pages' tops (see `Drive`):
///
/// - `readout` draws each page's top measures over it, for screenshots: where its content starts,
///   how far its title line sits from the back button's middle, how often its top moved while it
///   showed, whether it showed before it was measured, and what the bar names
/// - `refresh` shows the front page's pull-to-refresh spinner as a pull would
@MainActor
enum PageBarDrive {
    static func install() {
        Drive.steps.append { name, _ in
            switch name {
            case "readout": PageBarReadout.shown.on = true
            case "refresh": showRefresh()
            default: return false
            }
            return true
        }
    }

    private static func showRefresh() {
        guard let window = UIApplication.shared.connectedScenes.compactMap({ ($0 as? UIWindowScene)?.keyWindow }).first
        else { return }
        var queue: [UIView] = [window]
        while !queue.isEmpty {
            let next = queue.removeFirst()
            if let scroll = next as? UIScrollView, let refresh = scroll.refreshControl, shown(scroll) {
                refresh.beginRefreshing()
                scroll.setContentOffset(CGPoint(x: 0, y: -scroll.adjustedContentInset.top - refresh.frame.height), animated: true)
                return
            }
            queue.append(contentsOf: next.subviews)
        }
    }

    private static func shown(_ view: UIView) -> Bool {
        var current: UIView? = view
        while let each = current {
            if each.isHidden || each.alpha < 0.01 { return false }
            current = each.superview
        }
        return true
    }
}

/// Whether pages draw their top measures, for screenshots.
@MainActor
@Observable
final class ReadoutSwitch {
    var on = false
}

/// A page's top measures, drawn over it for screenshots.
struct PageBarReadout: View {
    @MainActor static let shown = ReadoutSwitch()
    let top: PageTop

    private func number(_ value: CGFloat?) -> String { value.map { String(format: "%.1f", $0) } ?? "-" }

    var body: some View {
        let metrics = BarMetrics.shared
        let dx = top.roomOnScreen.map { $0.x - top.back.x }
        let dy = top.roomOnScreen.map { $0.y - top.back.y }
        let numbers = "top \(number(top.contentTop)) gap \(number(top.headerGap)) line \(number(top.lineInHeader))/\(number(top.lineHeight)) "
            + "align \(number(dx)),\(number(dy)) moves \(top.moves) waited \(top.waited ? "yes" : "no")"
        let bar = "bar \(top.barTitle.isEmpty ? "-" : top.barTitle) back \(number(metrics.back.x)),\(number(metrics.back.y)) "
            + "height \(number(metrics.height)) page \(number(top.page?.screen.minY)),\(number(top.page?.size.height))"
        VStack(spacing: 2) {
            Text(numbers)
            Text(bar)
            if !top.lastMove.isEmpty { Text("moved: \(top.lastMove)") }
        }
        .font(.system(size: 11, weight: .semibold, design: .monospaced))
        .foregroundStyle(.yellow)
        .padding(6)
        .background(.black.opacity(0.8))
        .padding(.bottom, 140)
        .allowsHitTesting(false)
    }
}
#endif

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
            case "readout": PageBarReadout.on = true
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

/// A page's top measures, drawn over it for screenshots.
struct PageBarReadout: View {
    @MainActor static var on = false
    let top: PageTop

    var body: some View {
        let align = top.lineOnScreen.flatMap { line in top.back.map { top.own(line) - $0.y } }
        Text(String(format: "top %.1f  align %@  moves %d  waited %@  bar %@",
                    top.contentTop, align.map { String(format: "%.1f", $0) } ?? "-", top.moves,
                    top.waited ? "yes" : "no", top.barTitle.isEmpty ? "-" : top.barTitle))
            .font(.system(size: 11, weight: .semibold, design: .monospaced))
            .foregroundStyle(.yellow)
            .padding(6)
            .background(.black.opacity(0.8))
            .padding(.bottom, 140)
            .allowsHitTesting(false)
    }
}
#endif

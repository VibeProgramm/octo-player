import CoreText
import OctoKit
import SwiftUI
import UIKit

/// A page's big title: 32 point Black, scrolling with the page under the bar. Always whole:
/// never cut short and never broken inside a word. A long title wraps between its words; a
/// single word too wide for the line on its own is set just small enough to fit (never below
/// half size). It names its page for the bar, which shows the name once these capitals have
/// reached the status bar.
struct ScreenTitle: View {
    let text: String
    /// The largest size the title may take, when its line has a limit (beside the bar's back button).
    var maxSize: CGFloat?
    @State private var width: CGFloat = 0
    @Environment(PageTop.self) private var top: PageTop?
    @ScaledMetric(relativeTo: .largeTitle) private var size: CGFloat = 32

    init(_ text: String, maxSize: CGFloat? = nil) {
        self.text = text
        self.maxSize = maxSize
    }

    var body: some View {
        let base = min(size, maxSize ?? size)
        let scale = TitleFit.scale(text, size: base, weight: .black, tracking: octoDisplayTracking, width: width)
        let font = UIFont.systemFont(ofSize: base * scale, weight: .black)
        let drawn = width > 0 ? base * scale : nil
        Text(breakableText(text))
            .font(.system(size: base * scale, weight: .black))
            .tracking(octoDisplayTracking)
            .foregroundStyle(.white)
            .fixedSize(horizontal: false, vertical: true)
            .modifier(TitleTopReport(inset: font.ascender - font.capHeight))
            .frame(maxWidth: .infinity, alignment: .leading)
            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 16)
            .accessibilityLabel(text)
            .accessibilityAddTraits(.isHeader)
            .onChange(of: text, initial: true) { _, now in top?.titleName = now }
            .onChange(of: drawn, initial: true) { _, now in top?.titleSize = now }
    }
}

/// Reports where a page's big title's capitals start on screen (`inset` below the view's top),
/// for the bar to name the page once they reach the status bar. A title a lazy list has dropped
/// counts as gone; shown again (the page back in front), it reports its last place until it is
/// measured anew.
struct TitleTopReport: ViewModifier {
    var inset: CGFloat = 0
    @Environment(PageTop.self) private var top: PageTop?
    @State private var last: CGFloat?

    func body(content: Content) -> some View {
        content
            .onGeometryChange(for: CGFloat.self) { $0.frame(in: .global).minY } action: { y in
                last = y + inset
                top?.titleTop = y + inset
            }
            .onAppear { if let last { top?.titleTop = last } }
            .onDisappear { top?.titleTop = -.greatestFiniteMagnitude }
    }
}

/// A section's title inside a page: 18 point Bold, white.
struct SectionTitle: View {
    let text: String

    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text)
            .font(.octoSection)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.vertical, 10)
            .accessibilityAddTraits(.isHeader)
    }
}

/// How small a title's type must be for its widest unbreakable stretch to fit the line.
enum TitleFit {
    static func scale(_ text: String, size: CGFloat, weight: UIFont.Weight, tracking: CGFloat, width: CGFloat) -> CGFloat {
        guard width > 0, !text.isEmpty else { return 1 }
        var scale = 1.0
        // Type does not scale quite evenly, so it is measured again after each step.
        for _ in 0..<3 {
            let font = UIFont.systemFont(ofSize: size * scale, weight: weight)
            let step = fitScale(widest: widestStretch(breakableText(text), font: font, tracking: tracking), width: Double(width))
            if step >= 1 { break }
            scale = max(titleLeastShare, scale * step)
            if scale <= titleLeastShare { break }
        }
        return CGFloat(scale)
    }

    /// The widest stretch of the text a line may not break inside: a word, the part of one a line
    /// may end after (a hyphen's), or one character of a script written without spaces.
    static func widestStretch(_ text: String, font: UIFont, tracking: CGFloat) -> Double {
        let string = text as NSString
        let tokenizer = CFStringTokenizerCreate(nil, string as CFString, CFRange(location: 0, length: string.length),
                                                kCFStringTokenizerUnitLineBreak, nil)
        var widest = 0.0
        var kind = CFStringTokenizerAdvanceToNextToken(tokenizer)
        while kind != [] {
            let range = CFStringTokenizerGetCurrentTokenRange(tokenizer)
            let piece = string.substring(with: NSRange(location: range.location, length: range.length))
                .trimmingCharacters(in: .whitespacesAndNewlines.union(CharacterSet(charactersIn: "\u{200B}")))
            if !piece.isEmpty {
                let measured = (piece as NSString).size(withAttributes: [.font: font, .kern: tracking]).width
                widest = max(widest, Double(measured))
            }
            kind = CFStringTokenizerAdvanceToNextToken(tokenizer)
        }
        return widest
    }
}

extension View {
    /// A pushed page under the system bar: the bar's glass back button at its top left, the
    /// system's edge effect over whatever scrolls under the bar, and the page's name in the bar
    /// once its big title's capitals reach the status bar. A page with `ownBack` has its title on
    /// the button's line: its `PageHeader` leaves the button's room, and the page starts just high
    /// enough for the title's capitals to sit on the button's middle. Other pages start under the
    /// status bar.
    func detailPage(ownBack: Bool = false) -> some View {
        modifier(PageBar(back: true, titleBesideBack: ownBack))
    }

    /// A tab's first page: no back button, the page starting under the status bar, and its name in
    /// the system bar once its big title's capitals reach the status bar. Without `showsBar` the
    /// bar stays hidden, for a page whose top never scrolls.
    func rootPage(showsBar: Bool = true) -> some View {
        modifier(PageBar(back: false, titleBesideBack: false, showsBar: showsBar))
    }

    /// Room under a page's content for the floating bar.
    func barClearance() -> some View {
        contentMargins(.bottom, ShellMeasures.clearance, for: .scrollContent)
    }
}

/// Space a page under artwork leaves at its top, clear of the bar's back button.
let detailTopGap: CGFloat = 56

/// What going back does on a page that has something to close first, like a selection or a
/// search: set by the page, run by the back button and the edge swipe in place of leaving.
@MainActor
final class BackHandler: Equatable {
    var run: () -> Void = {}

    /// The handler of the page in front, for the edge swipe.
    static weak var current: BackHandler?

    nonisolated static func == (a: BackHandler, b: BackHandler) -> Bool { a === b }
}

struct BackHandlerKey: PreferenceKey {
    static var defaultValue: BackHandler? { nil }

    static func reduce(value: inout BackHandler?, nextValue: () -> BackHandler?) {
        value = nextValue() ?? value
    }
}

extension View {
    /// While `run` is set, going back runs it instead of leaving the page.
    func backAction(_ run: (() -> Void)?) -> some View {
        modifier(BackActionSetter(run: run))
    }
}

private struct BackActionSetter: ViewModifier {
    let run: (() -> Void)?
    @State private var handler = BackHandler()

    func body(content: Content) -> some View {
        if let run { handler.run = run }
        return content.preference(key: BackHandlerKey.self, value: run == nil ? nil : handler)
    }
}

/// Where a page's top parts are, so its title line can sit on the system back button's middle
/// and the bar can name the page once the big title's capitals reach the status bar. Places read
/// on screen are turned into the page's own points first, since a tab switch scales a whole tab.
@MainActor
@Observable
final class PageTop {
    /// Whether the page's title line sits beside the back button.
    let titleBesideBack: Bool
    /// The page on screen, and its own size.
    var screenFrame: CGRect = .zero
    var ownSize: CGSize = .zero
    /// The system back button's middle, in the page's points; until measured, the last page's.
    var back: CGPoint? = PageTop.lastBack
    /// The status bar's height.
    var status: CGFloat = PageTop.windowTop()
    /// The system bar's bottom on screen.
    var barBottomOnScreen: CGFloat?
    /// The middle of the title line below the top of the page's `PageHeader`, the same line on
    /// screen, and the header's top on screen.
    var lineInHeader: CGFloat?
    var lineOnScreen: CGFloat?
    var headerTopOnScreen: CGFloat? { didSet { measureGap() } }
    /// The header's top below the content's top, measured once with the page at rest (a list may
    /// add room of its own above it).
    var headerGap: CGFloat?
    /// The page's name for the bar: its own, from `inlineTitle`, else its big title's.
    var name: String?
    var titleName: String?
    /// The size the big title is drawn at, once its width is known.
    var titleSize: CGFloat?
    /// Where the big title's capitals start on screen.
    var titleTop: CGFloat = .greatestFiniteMagnitude
    /// The page showed before it was measured, after waiting too long.
    var waited = false
    /// How many times the content's top moved while the page was showing.
    var moves = 0

    /// The last back button measured, in a page's points, for the next page to start with.
    static var lastBack: CGPoint?

    init(titleBesideBack: Bool) { self.titleBesideBack = titleBesideBack }

    /// The status bar's height from the window, known before anything is laid out.
    static func windowTop() -> CGFloat {
        UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first?.safeAreaInsets.top ?? 0
    }

    /// A height read on screen, in the page's own points.
    func own(_ y: CGFloat) -> CGFloat {
        guard screenFrame.height > 0, ownSize.height > 0 else { return y }
        return (y - screenFrame.minY) * ownSize.height / screenFrame.height
    }

    /// A point read on screen, in the page's own points.
    func own(_ point: CGPoint) -> CGPoint {
        guard screenFrame.width > 0, ownSize.width > 0 else { return point }
        return CGPoint(x: (point.x - screenFrame.minX) * ownSize.width / screenFrame.width, y: own(point.y))
    }

    /// The system bar's bottom, in the page's points.
    var barBottom: CGFloat? { barBottomOnScreen.map { own($0) } }

    /// The middle of the title line below the content's top.
    var line: CGFloat? {
        guard let headerGap, let lineInHeader else { return nil }
        return headerGap + lineInHeader
    }

    /// Where the page's content starts: with its title beside the back button, high enough for
    /// the title's capitals to sit on the button's middle; else under the status bar.
    var contentTop: CGFloat {
        guard titleBesideBack, let back, let line else { return status }
        return back.y - line
    }

    /// Whether the page can be shown where it belongs.
    var settled: Bool {
        status > 0 && (!titleBesideBack || (back != nil && line != nil && titleSize != nil) || waited)
    }

    /// The big title's capitals have reached the status bar.
    var gone: Bool { own(titleTop) < status }

    /// What the bar's title shows: the page's name once the big title's capitals reach the status
    /// bar, and nothing before, since a named bar softens everything under it, line one too.
    var barTitle: String { gone ? (name ?? titleName ?? "") : "" }

    /// The largest big title whose capitals, on the button's middle, stay 4 points under the
    /// status bar.
    var titleRoom: CGFloat? {
        guard titleBesideBack, let back, status > 0 else { return nil }
        let capShare = UIFont.systemFont(ofSize: 100, weight: .black).capHeight / 100
        return 2 * (back.y - status - 4) / capShare
    }

    /// The page's frame changed: a gap waiting on it can be taken now.
    func framed(screen: CGRect, own size: CGSize) {
        screenFrame = screen
        ownSize = size
        measureGap()
    }

    /// Takes the header's gap the first time the header is seen: the page is at rest then, and
    /// until the gap is known its content starts under the status bar.
    private func measureGap() {
        guard headerGap == nil, let headerTopOnScreen, ownSize.height > 0 else { return }
        headerGap = own(headerTopOnScreen) - status
    }
}

/// The page on screen and its own size.
private struct PageGeometry: Equatable {
    let screen: CGRect
    let own: CGSize
}

/// A page under the system bar: the bar holds the back button on a pushed page and the page's
/// name once its big title's capitals reach the status bar; the page runs under the bar, where
/// the system softens whatever scrolls there. The page waits unseen until it can be drawn where
/// it belongs (a fraction of a second at most).
private struct PageBar: ViewModifier {
    let back: Bool
    var showsBar = true
    @Environment(\.dismiss) private var dismiss
    @State private var handler: BackHandler?
    @State private var top: PageTop
    @State private var shownTop: CGFloat?

    init(back: Bool, titleBesideBack: Bool, showsBar: Bool = true) {
        self.back = back
        self.showsBar = showsBar
        _top = State(initialValue: PageTop(titleBesideBack: titleBesideBack))
    }

    func body(content: Content) -> some View {
        content
            .environment(top)
            .safeAreaPadding(.top, top.contentTop)
            .ignoresSafeArea(.container, edges: .top)
            .opacity(top.settled ? 1 : 0)
            .onGeometryChange(for: PageGeometry.self) { PageGeometry(screen: $0.frame(in: .global), own: $0.size) } action: { page in
                top.framed(screen: page.screen, own: page.own)
            }
            .background {
                BarFrameProbe { bottom in
                    if top.barBottomOnScreen != bottom { top.barBottomOnScreen = bottom }
                }
            }
            #if DEBUG
            .overlay(alignment: .bottom) { if PageBarReadout.on { PageBarReadout(top: top) } }
            #endif
            .navigationBarBackButtonHidden(true)
            .navigationTitle(top.barTitle)
            .toolbarTitleDisplayMode(.inline)
            .toolbar(showsBar ? .visible : .hidden, for: .navigationBar)
            .toolbar {
                if back {
                    ToolbarItem(placement: .topBarLeading) {
                        Button(action: goBack) { IconView(.back, size: 20, color: .white) }
                            .accessibilityLabel("Back")
                            .onGeometryChange(for: CGPoint.self) { CGPoint(x: $0.frame(in: .global).midX, y: $0.frame(in: .global).midY) } action: { middle in
                                let own = top.own(middle)
                                top.back = own
                                PageTop.lastBack = own
                            }
                    }
                }
            }
            .onChange(of: top.settled, initial: true) { _, settled in
                if settled, shownTop == nil { shownTop = top.contentTop }
            }
            .onChange(of: top.contentTop) { _, now in
                guard let shown = shownTop, shown != now else { return }
                top.moves += 1
                shownTop = now
            }
            .onAppear { if top.status == 0 { top.status = PageTop.windowTop() } }
            .task {
                try? await Task.sleep(for: .milliseconds(300))
                if !top.settled { top.waited = true }
            }
            .onPreferenceChange(BackHandlerKey.self) { value in
                guard back else { return }
                Task { @MainActor in
                    handler = value
                    BackHandler.current = value
                }
            }
            .onDisappear { if back, BackHandler.current === handler { BackHandler.current = nil } }
    }

    private func goBack() {
        if let handler { handler.run() } else { dismiss() }
    }
}

/// Reads the bottom of the system bar on screen, from the navigation controller showing the page.
private struct BarFrameProbe: UIViewRepresentable {
    let report: (CGFloat) -> Void

    func makeUIView(context: Context) -> ProbeView { ProbeView(report: report) }

    func updateUIView(_ view: ProbeView, context: Context) {
        view.report = report
        view.measure()
    }

    final class ProbeView: UIView {
        var report: (CGFloat) -> Void
        private var last: CGFloat?

        init(report: @escaping (CGFloat) -> Void) {
            self.report = report
            super.init(frame: .zero)
            isUserInteractionEnabled = false
        }

        required init?(coder: NSCoder) { nil }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            measure()
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            measure()
        }

        func measure() {
            guard let window else { return }
            var next: UIResponder? = self
            while let responder = next {
                if let bar = (responder as? UIViewController)?.navigationController?.navigationBar {
                    let bottom = bar.convert(bar.bounds, to: window).maxY
                    guard bottom != last else { return }
                    last = bottom
                    let report = report
                    DispatchQueue.main.async { report(bottom) }
                    return
                }
                next = responder.next
            }
        }
    }
}

/// With the bar's own back button replaced by the page's, the edge swipe back still works.
extension UINavigationController: @retroactive UIGestureRecognizerDelegate {
    override open func viewDidLoad() {
        super.viewDidLoad()
        interactivePopGestureRecognizer?.delegate = self
    }

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard viewControllers.count > 1 else { return false }
        if let handler = MainActor.assumeIsolated({ BackHandler.current }) {
            MainActor.assumeIsolated { handler.run() }
            return false
        }
        return true
    }
}

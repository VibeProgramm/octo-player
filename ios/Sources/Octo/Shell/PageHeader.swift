import OctoKit
import SwiftUI
import UIKit

/// The top a page whose back button sits on its title's line leaves above that line.
let pageTopGap: CGFloat = 8

/// Line one of a page: on a pushed page the back button, then the big title, alone on its line,
/// the button on the middle of the title's capitals. `count`, when given, follows the title in
/// muted words only while both fit on the line; otherwise the title stands alone. The edge swipe
/// goes back as the button does, and a page busy with something (a selection, a search) closes
/// that first.
struct PageHeader: View {
    let title: String
    var count: String?
    var back = true
    @Environment(\.dismiss) private var dismiss
    @Environment(PageTop.self) private var top: PageTop?
    @ScaledMetric(relativeTo: .largeTitle) private var size: CGFloat = 32

    init(_ title: String, count: String? = nil, back: Bool = true) {
        self.title = title
        self.count = count
        self.back = back
    }

    var body: some View {
        HStack(alignment: .pageTitleLine, spacing: 0) {
            if back {
                GlazedIconButton(.back, label: "Back", size: 40) { goBack() }
                    .alignmentGuide(.pageTitleLine) { d in d[VerticalAlignment.center] }
                    .padding(.leading, 16)
                    // Once it scrolls away, the page's floating back button stands in for it.
                    .onScrollVisibilityChange(threshold: 0.5) { shown in top?.gone = !shown }
                    .onAppear { top?.gone = false }
                    .onDisappear { top?.gone = true }
            }
            if let count {
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .firstTextBaseline, spacing: 0) {
                        titleText.fixedSize(horizontal: true, vertical: false)
                        Text(count)
                            .font(.octoBodySmall)
                            .foregroundStyle(OctoColor.textMuted)
                            .lineLimit(1)
                            .fixedSize()
                            .padding(.leading, -8)
                            .padding(.trailing, 20)
                        Spacer(minLength: 0)
                    }
                    titleText
                }
                .alignmentGuide(.pageTitleLine) { d in d[.firstTextBaseline] - capHeight / 2 }
            } else {
                titleText
                    .alignmentGuide(.pageTitleLine) { d in d[.firstTextBaseline] - capHeight / 2 }
            }
        }
    }

    private var titleText: some View {
        ScreenTitle(title).padding(.leading, back ? -8 : 0)
    }

    /// The title's cap height, for the back button to sit on the middle of its capitals.
    private var capHeight: CGFloat { UIFont.systemFont(ofSize: size, weight: .black).capHeight }

    private func goBack() {
        if let handler = BackHandler.current { handler.run() } else { dismiss() }
    }
}

extension VerticalAlignment {
    private enum PageTitleLine: AlignmentID {
        static func defaultValue(in dimensions: ViewDimensions) -> CGFloat { dimensions[VerticalAlignment.center] }
    }

    /// The middle of a page title's capitals, for the back button beside it.
    static let pageTitleLine = VerticalAlignment(PageTitleLine.self)
}

/// An item of a control line that may move into More when the line runs out of room, lowest
/// `priority` first, with the More row that stands in for it there and does the same thing
/// ("Sort: Date liked"). It sits before the segments, or with `trailing` just before the actions.
struct LineOverflow: Identifiable {
    let id: String
    let priority: Int
    var trailing = false
    let item: AnyView
    let row: AnyView

    init<Item: View, Row: View>(_ id: String, priority: Int, trailing: Bool = false,
                                @ViewBuilder item: () -> Item, @ViewBuilder row: () -> Row) {
        self.id = id
        self.priority = priority
        self.trailing = trailing
        self.item = AnyView(item())
        self.row = AnyView(row())
    }
}

/// Line two of a page: everything under the title on one line, never scrolling sideways, fitted
/// to the phone it is on. The page's own control first, then its overflow items, then its chips
/// or segments (its main choice), then its actions held at the right (Play, Shuffle). The main
/// choice and the actions never move. When the line runs out of room the overflow items move into
/// More, which shows for them even on a page with no More of its own, in their order; only then
/// do the segments' labels give way, and past that the segments are `chipsMenu`, a menu button.
struct ControlLine<Lead: View, Chips: View, Actions: View>: View {
    let lead: Lead
    let chips: Chips
    let actions: Actions
    /// Chips that take the room left, like segments, rather than their own width.
    var fills = false
    var overflow: [LineOverflow] = []
    /// The page's own More rows, under any items moved there.
    var more: AnyView?
    /// The segments as one menu button, for when even shrunk they do not fit.
    var chipsMenu: AnyView?
    @State private var room: CGFloat = 0
    @State private var fixedWidth: CGFloat = 0
    @State private var chipsWidth: CGFloat = 0
    @State private var widths: [String: CGFloat] = [:]

    init(fills: Bool = false, overflow: [LineOverflow] = [], more: AnyView? = nil, chipsMenu: AnyView? = nil,
         @ViewBuilder lead: () -> Lead, @ViewBuilder chips: () -> Chips, @ViewBuilder actions: () -> Actions) {
        self.fills = fills
        self.overflow = overflow
        self.more = more
        self.chipsMenu = chipsMenu
        self.lead = lead()
        self.chips = chips()
        self.actions = actions()
    }

    var body: some View {
        let fit = currentFit
        HStack(spacing: 8) {
            lead
            ForEach(overflow.filter { !$0.trailing && !fit.moved.contains($0.id) }) { piece in piece.item }
            if fit.segmentsAsMenu, let chipsMenu {
                chipsMenu
                Spacer(minLength: 0)
            } else if fills {
                chips.frame(maxWidth: .infinity)
            } else {
                HStack(spacing: 8) { chips }
                Spacer(minLength: 0)
            }
            ForEach(overflow.filter { $0.trailing && !fit.moved.contains($0.id) }) { piece in piece.item }
            HStack(spacing: 0) {
                actions
                if fit.showsMore {
                    GlassMoreMenu {
                        ForEach(overflow.filter { fit.moved.contains($0.id) }) { piece in piece.row }
                        if let more { more }
                    }
                }
            }
            .fixedSize()
        }
        // The line always spans its room, so what it measures is the room, not what it holds.
        .frame(maxWidth: .infinity, alignment: .leading)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { room = $0 }
        .background(alignment: .leading) { measures }
        .padding(.leading, 20)
        // Round buttons' targets reach 7 past their glass, so the glass ends 20 in.
        .padding(.trailing, 13)
        .padding(.bottom, 12)
    }

    /// What goes where at the room measured; everything shows until the room is known.
    private var currentFit: LineFit {
        guard room > 0 else { return LineFit(showsMore: more != nil) }
        let pieces = overflow.map { LinePiece($0.id, width: Double(widths[$0.id] ?? 0), priority: $0.priority) }
        let fixed = fixedWidth + (fills ? 0 : chipsWidth + 8)
        return fitControlLine(width: Double(room), fixed: Double(fixed), segments: fills ? Double(chipsWidth) : nil,
                              pieces: pieces, ownMore: more != nil)
    }

    /// Each part at its natural width, unseen, for the fit.
    private var measures: some View {
        ZStack(alignment: .leading) {
            HStack(spacing: 8) {
                lead
                HStack(spacing: 0) { actions }
            }
            .fixedSize()
            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { fixedWidth = $0 }
            HStack(spacing: 8) { chips }
                .fixedSize()
                .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { chipsWidth = $0 }
            ForEach(overflow) { piece in
                piece.item
                    .fixedSize()
                    .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { widths[piece.id] = $0 }
            }
        }
        .hidden()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

extension ControlLine where Chips == EmptyView {
    init(overflow: [LineOverflow] = [], more: AnyView? = nil, @ViewBuilder lead: () -> Lead, @ViewBuilder actions: () -> Actions) {
        self.init(overflow: overflow, more: more, lead: lead, chips: { EmptyView() }, actions: actions)
    }
}

extension ControlLine where Lead == EmptyView {
    /// No control of the page's own: `fills` is asked for so the chips are never taken for one.
    init(fills: Bool, overflow: [LineOverflow] = [], more: AnyView? = nil, chipsMenu: AnyView? = nil,
         @ViewBuilder chips: () -> Chips, @ViewBuilder actions: () -> Actions) {
        self.init(fills: fills, overflow: overflow, more: more, chipsMenu: chipsMenu, lead: { EmptyView() }, chips: chips,
                  actions: actions)
    }
}

extension ControlLine where Lead == EmptyView, Chips == EmptyView {
    init(overflow: [LineOverflow] = [], more: AnyView? = nil, @ViewBuilder actions: () -> Actions) {
        self.init(overflow: overflow, more: more, lead: { EmptyView() }, chips: { EmptyView() }, actions: actions)
    }
}

/// Play and Shuffle as round glass buttons, Play lit, for the end of a control line. Faint and
/// out of reach while there is nothing to play; Play turns while `loading`.
struct RoundPlayShuffle: View {
    var enabled = true
    var loading = false
    let play: () -> Void
    let shuffle: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            if loading {
                Spinner(size: 18)
                    .frame(width: 34, height: 34)
                    .glassEffect(LibraryLook.lifted, in: .circle)
                    .frame(width: 48, height: 48)
            } else {
                GlassIconButton(icon: .play, label: "Play", lit: true, action: play)
            }
            GlassIconButton(icon: .shuffle, label: "Shuffle", action: shuffle)
        }
        .disabled(!enabled || loading)
        .opacity(enabled || loading ? 1 : OctoInkColor.disabled)
    }
}

/// More, a round glass button that opens the system menu with a page's other actions; with
/// another icon and label, any such menu (a "+" with the ways to make something).
struct GlassMoreMenu<Content: View>: View {
    var icon: OctoIcon = .more
    var label = "More"
    let content: Content

    init(icon: OctoIcon = .more, label: String = "More", @ViewBuilder content: () -> Content) {
        self.icon = icon
        self.label = label
        self.content = content()
    }

    var body: some View {
        Menu {
            content
        } label: {
            IconView(icon, size: 17, color: .white)
                .frame(width: 34, height: 34)
                .glassEffect(LibraryLook.resting, in: .circle)
                .frame(width: 48, height: 48)
                .contentShape(Circle())
        }
        .accessibilityLabel(label)
    }
}

/// "Keep downloaded" as a switch in a More menu, for a playlist, or for Liked songs when
/// `playlistId` is nil: their songs only on a server download to the phone and follow the list.
/// Left out when there is nothing to download and it is off.
struct KeepDownloadedToggle: View {
    let tracks: [LibraryTrack]
    var playlistId: String?
    @Environment(OfflineDownloads.self) private var offline

    var body: some View {
        let checked = keptDownloaded(offline, playlistId: playlistId)
        if offersKeepDownloaded(offline, tracks: tracks, playlistId: playlistId) {
            Toggle(isOn: Binding(get: { checked }, set: { on in
                if let playlistId { offline.setPlaylistKept(playlistId, on) } else { offline.setKeepLiked(on) }
            })) {
                Label("Keep downloaded", systemImage: OctoIcon.download.symbol)
            }
        }
    }
}

/// While a list is kept downloaded, how far its downloads are, for its details line; nil while
/// it is not kept.
@MainActor
func keptDownloadedWords(_ offline: OfflineDownloads, tracks: [LibraryTrack], playlistId: String?) -> String? {
    guard keptDownloaded(offline, playlistId: playlistId) else { return nil }
    let serverOnly = tracks.filter { !$0.onPhone && !$0.isFound }
    let done = serverOnly.filter { offline.byTrack[$0.id]?.state == .done }.count
    return keptSummary(serverOnly: serverOnly.count, done: done)
}

/// Whether Keep downloaded is offered: while it is on, or while some songs are only on a server.
@MainActor
func offersKeepDownloaded(_ offline: OfflineDownloads, tracks: [LibraryTrack], playlistId: String?) -> Bool {
    keptDownloaded(offline, playlistId: playlistId) || tracks.contains { !$0.onPhone && !$0.isFound }
}

/// Whether a playlist, or Liked songs when `playlistId` is nil, is kept downloaded.
@MainActor
func keptDownloaded(_ offline: OfflineDownloads, playlistId: String?) -> Bool {
    let prefs = offline.settings.offline
    return playlistId.map { prefs.keptPlaylists.contains($0) } ?? prefs.keepLiked
}

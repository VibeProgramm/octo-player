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

/// Line two of a page: everything under the title on one line. The page's own control first
/// (Sort, a country), then its chips or segments, which scroll sideways when they run long, then
/// its actions held at the right (Play, Shuffle, More).
struct ControlLine<Lead: View, Chips: View, Actions: View>: View {
    let lead: Lead
    let chips: Chips
    let actions: Actions
    /// Chips that size themselves to the room left, like segments, rather than scroll.
    var fills = false

    init(fills: Bool = false, @ViewBuilder lead: () -> Lead, @ViewBuilder chips: () -> Chips, @ViewBuilder actions: () -> Actions) {
        self.fills = fills
        self.lead = lead()
        self.chips = chips()
        self.actions = actions()
    }

    var body: some View {
        HStack(spacing: 8) {
            lead
            if fills {
                chips.frame(maxWidth: .infinity)
            } else {
                ScrollView(.horizontal) {
                    HStack(spacing: 8) { chips }
                }
                .scrollIndicators(.hidden)
                .fixedSize(horizontal: false, vertical: true)
            }
            HStack(spacing: 0) { actions }
        }
        .padding(.leading, 20)
        // Round buttons' targets reach 7 past their glass, so the glass ends 20 in.
        .padding(.trailing, 13)
        .padding(.bottom, 12)
    }
}

extension ControlLine where Chips == EmptyView {
    init(@ViewBuilder lead: () -> Lead, @ViewBuilder actions: () -> Actions) {
        self.init(lead: lead, chips: { EmptyView() }, actions: actions)
    }
}

extension ControlLine where Lead == EmptyView {
    /// No control of the page's own: `fills` is asked for so the chips are never taken for one.
    init(fills: Bool, @ViewBuilder chips: () -> Chips, @ViewBuilder actions: () -> Actions) {
        self.init(fills: fills, lead: { EmptyView() }, chips: chips, actions: actions)
    }
}

extension ControlLine where Lead == EmptyView, Chips == EmptyView {
    init(@ViewBuilder actions: () -> Actions) {
        self.init(lead: { EmptyView() }, chips: { EmptyView() }, actions: actions)
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

#if DEBUG
import SwiftUI

/// Drive steps for row swipes (see `Drive`):
///
/// - `swipe` lets go of a swipe right on the first song row under the bar that can be swiped
///   right, as a finger would: the row plays next or joins the queue as Settings say
/// - `swipe:peek` holds the first such row's own swipe (outside system lists) at a third of its
///   width for four seconds, so the wash under it can be shot
@MainActor
final class SwipeDrive {
    static let shared = SwipeDrive()
    /// Rows only take part while the app runs drive steps.
    static let active = ProcessInfo.processInfo.environment["OCTO_DRIVE"] != nil

    /// A row: its top on screen, what letting go does, and its peek.
    struct Row {
        var top: CGFloat
        let run: () -> Void
        let peek: (() -> Void)?
    }

    private var rows: [UUID: Row] = [:]

    func update(_ id: UUID, _ row: Row) { rows[id] = row }
    func remove(_ id: UUID) { rows[id] = nil }

    static func install() {
        Drive.steps.append { name, value in
            guard name == "swipe" else { return false }
            shared.fire(peek: value == "peek")
            return true
        }
    }

    /// The row nearest the top that sits under the bar (whose top is at least 120 points down).
    private func fire(peek: Bool) {
        let first = rows.values.filter { $0.top >= 120 && (!peek || $0.peek != nil) }.min { $0.top < $1.top }
        if peek { first?.peek?() } else { first?.run() }
    }
}

/// Puts a row that can be swiped right in `SwipeDrive`'s reach while it is on screen.
struct SwipeDriveMark: ViewModifier {
    let run: (() -> Void)?
    var peek: (() -> Void)?
    @State private var id = UUID()

    func body(content: Content) -> some View {
        if SwipeDrive.active, let run {
            content
                .onGeometryChange(for: CGFloat.self) { $0.frame(in: .global).minY } action: { top in
                    SwipeDrive.shared.update(id, .init(top: top, run: run, peek: peek))
                }
                .onDisappear { SwipeDrive.shared.remove(id) }
        } else {
            content
        }
    }
}

/// Drive steps for putting songs in the queue the ways a person does (see `Drive`):
///
/// - `queue:menu-next` / `queue:menu-last`: the library's first song, as its menu's Play next /
///   Add to queue rows do
/// - `queue:collection-next` / `queue:collection-last`: the library's first five songs, as a
///   collection menu's rows do
/// - `queue:bar-next` / `queue:bar-last`: the selection bar's Play next / Add to queue (after
///   `select:N`)
/// - `latetap`: shows, in a message, whether the message window takes a tap on a pill's place
///   0.3 s and 0.7 s after the pill has gone
@MainActor
enum QueueDrive {
    /// The selection bar's queue buttons, while one is on screen.
    static var bar: ((Bool) -> Void)?

    static func install(router: Router) {
        Drive.steps.append { name, value in
            switch name {
            case "queue": await run(value)
            case "latetap": router.show(await ToastWindow.lateTapCheck(router: router))
            default: return false
            }
            return true
        }
    }

    private static func run(_ value: String) async {
        let next = value.hasSuffix("next")
        if value.hasPrefix("bar") {
            bar?(next)
            return
        }
        guard let actions = LibraryActions.current else { return }
        let songs = await actions.driveSongs(value.hasPrefix("menu") ? 1 : 5)
        if value.hasPrefix("menu") {
            guard let first = songs.first else { return }
            if next { actions.playNextUndoable(first.queueSong) } else { actions.addToQueueUndoable(first.queueSong) }
        } else if next {
            actions.playNext(songs)
        } else {
            actions.addToQueue(songs)
        }
    }
}
#endif

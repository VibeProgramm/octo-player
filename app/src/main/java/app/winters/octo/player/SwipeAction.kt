package app.winters.octo.player

// What swiping a song row right does: puts it right after the song that
// is on, at the end of the queue, or nothing. Kept by name, as the other
// apps keep it.
enum class SwipeAction(val label: String, val detail: String) {
    PlayNext("Play next", "After the current song"),
    AddToQueue("Add to queue", "At the end of the queue"),
    Off("Off", "Swiping does nothing"),
}

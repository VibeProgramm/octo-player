package app.winters.octo.playback

// What the message says when songs are put in the queue: next, or at its end.
fun queueAddWords(count: Int, next: Boolean): String = when {
    count == 1 -> if (next) "Playing next" else "Added to queue"
    next -> "$count songs playing next"
    else -> "$count songs added to queue"
}

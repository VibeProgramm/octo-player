/// What the message says when songs are put in the queue: next, or at its end.
public func queueAddWords(count: Int, next: Bool) -> String {
    if count == 1 { return next ? "Playing next" : "Added to queue" }
    return next ? "\(count) songs playing next" : "\(count) songs added to queue"
}

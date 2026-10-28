import Foundation
@testable import OctoKit
import Testing

/// A pill's place keeps taking taps for 0.6 s after it leaves, only inside its frame.
struct LateTapGuardTests {
    let left = Date(timeIntervalSince1970: 1000)
    let pill = CGRect(x: 20, y: 700, width: 335, height: 48)

    @Test func takesATapInThePillsPlaceRightAfter() {
        var place = LateTapGuard()
        place.start(at: pill, now: left)
        #expect(place.takes(CGPoint(x: 300, y: 720), now: left.addingTimeInterval(0.3)))
    }

    @Test func letsATapOutsideThePlaceThrough() {
        var place = LateTapGuard()
        place.start(at: pill, now: left)
        #expect(!place.takes(CGPoint(x: 300, y: 600), now: left.addingTimeInterval(0.3)))
    }

    @Test func letsTapsThroughOnceTheMomentIsOver() {
        var place = LateTapGuard()
        place.start(at: pill, now: left)
        #expect(!place.takes(CGPoint(x: 300, y: 720), now: left.addingTimeInterval(0.6)))
    }

    @Test func takesNothingBeforeAPillHasLeft() {
        #expect(!LateTapGuard().takes(CGPoint(x: 300, y: 720), now: left))
    }
}

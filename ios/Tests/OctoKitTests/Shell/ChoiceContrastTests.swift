import Foundation
import Testing
@testable import OctoKit

@Suite struct ChoiceContrastTests {
    private let onAccent = OctoPalette.onAccent.hex | 0xFF00_0000

    @Test func theSelectedSegmentStandsOutFromItsTrackInEveryAccent() {
        for choice in AccentChoice.fixed {
            #expect(contrastRatio(choice.argb!, accentSurfaceArgb) >= 3.0, "\(choice.label)")
        }
    }

    @Test func darkWordsReadOnEveryAccentFill() {
        for choice in AccentChoice.fixed {
            #expect(contrastRatio(onAccent, choice.argb!) >= 4.5, "\(choice.label)")
        }
    }

    @Test func whiteWordsReadOnTheSelectedRowOfEveryAccent() {
        for choice in AccentChoice.fixed {
            let selected = mix(.black, RGBA(argb: choice.argb!), 0.40)
            #expect(contrastRatio(0xFFFF_FFFF, selected.hex | 0xFF00_0000) >= 4.5, "\(choice.label)")
        }
    }
}

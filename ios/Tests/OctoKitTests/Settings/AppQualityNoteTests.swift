@testable import OctoKit
import Testing

/// The note over this app's own quality names the answer that hands the choice to this app only when that
/// answer is on the page.
struct AppQualityNoteTests {
    @Test func namesTheAnswerWhenThisPhoneFollowsIsShown() {
        #expect(AccountQualityWords.appQualityNote(deviceShown: true)
            == "Your account's choice applies now. Choose \"Let\u{00A0}this\u{00A0}app\u{00A0}decide\" above to use these.")
    }

    @Test func saysOnlyWhatAppliesWhenItIsNot() {
        #expect(AccountQualityWords.appQualityNote(deviceShown: false) == "Your account's choice applies now.")
    }
}

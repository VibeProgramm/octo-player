import Foundation
import Testing
@testable import OctoKit

private let names = ["us": "United States", "jp": "Japan", "br": "Brazil", "de": "Germany", "kr": "South Korea"]
private func name(_ code: String) -> String { names[code] ?? code.uppercased() }

private func charts(country: String? = "us", setting: String? = "auto", pinned: [String] = [], maxPinned: Int = 3,
                    server: String? = "us") -> ChartChoices {
    ChartChoices(country: country, setting: setting, pinned: pinned, countries: ["us", "jp", "br", "de", "kr"],
                 maxPinned: maxPinned, serverCountry: server)
}

@Suite struct ChartCountryMenuTests {
    @Test func followingTheServerTheMenuStartsWithYours() {
        let menu = chartCountryMenu(charts(), name: name)
        #expect(menu.top == [CountryRow(id: chartCountryYours, code: "us", name: "Yours (United States)")])
    }

    @Test func aChosenCountryAlsoOffersTheServersAgain() {
        let menu = chartCountryMenu(charts(country: "br", setting: "br"), name: name)
        #expect(menu.top.map(\.id) == [chartCountryYours, chartCountryAuto])
        #expect(menu.top.map(\.name) == ["Yours (Brazil)", "Same as the server (United States)"])
        #expect(menu.top[1].code == "us")
    }

    @Test func aServerThatDoesNotSayItsCountryIsStillOffered() {
        let menu = chartCountryMenu(charts(country: "br", setting: "br", server: nil), name: name)
        #expect(menu.top[1] == CountryRow(id: chartCountryAuto, code: "", name: "Same as the server"))
    }

    @Test func everyOtherCountryFollowsByNameWithPinnedOnesMarked() {
        let menu = chartCountryMenu(charts(pinned: ["kr"]), name: name)
        #expect(menu.countries.map(\.code) == ["br", "de", "jp", "kr"])
        #expect(menu.countries.map(\.detail) == [nil, nil, nil, "Pinned"])
        #expect(menu.countries.allSatisfy { $0.id == $0.code })
    }

    @Test func withNoCountryKnownYoursHasNoName() {
        let menu = chartCountryMenu(charts(country: nil), name: name)
        #expect(menu.top.first?.name == "Yours")
        #expect(menu.countries.count == 5)
    }
}

@Suite struct ChartCountryChoicesTests {
    private let japan = CountryRow(id: "jp", code: "jp", name: "Japan")

    @Test func aCountryCanBeLookedAtUsedOrAddedAsPlaylists() {
        let choices = chartCountryChoices(japan, charts: charts(), name: name)
        #expect(choices.map(\.action) == [.look, .use, .pin])
        #expect(choices.map(\.title) == ["Just look here", "Use Japan for my charts", "Also add Japan's playlists"])
        #expect(choices.map(\.detail) == ["Nothing is saved", "Your chart playlists and Home switch to Japan, in every app",
                                          "Keeps yours and adds Popular right now · Japan and the rest"])
        #expect(choices.allSatisfy { $0.enabled })
    }

    @Test func aPinnedCountryOffersRemovingItsPlaylists() {
        let choices = chartCountryChoices(japan, charts: charts(pinned: ["jp"]), name: name)
        #expect(choices.map(\.action) == [.look, .use, .unpin])
        #expect(choices[2].title == "Remove Japan's playlists")
        #expect(choices[2].enabled)
    }

    @Test func withEveryPinTakenAddingCantBePicked() {
        let choices = chartCountryChoices(japan, charts: charts(pinned: ["br", "de", "kr"]), name: name)
        #expect(choices[2].action == .pin)
        #expect(!choices[2].enabled)
        #expect(choices[2].detail == "3 countries already have playlists")
        // A pinned one can always be removed, even with every pin taken.
        let pinned = chartCountryChoices(japan, charts: charts(pinned: ["jp", "de", "kr"]), name: name)
        #expect(pinned[2].action == .unpin && pinned[2].enabled)
    }

    @Test func theServersCountryOffersLookingAndFollowingItAgain() {
        let row = CountryRow(id: chartCountryAuto, code: "us", name: "Same as the server (United States)")
        let choices = chartCountryChoices(row, charts: charts(country: "br", setting: "br"), name: name)
        #expect(choices.map(\.action) == [.look, .use])
        #expect(choices.map(\.title) == ["Just look here", "Use the server's country for my charts"])
        #expect(choices[0].enabled)
        // Nothing to look at when the server does not say its country.
        let unknown = chartCountryChoices(CountryRow(id: chartCountryAuto, code: "", name: "Same as the server"),
                                          charts: charts(country: "br", setting: "br", server: nil), name: name)
        #expect(!unknown[0].enabled)
    }

    @Test func aNameEndingInSTakesOnlyTheApostrophe() {
        #expect(possessive("Japan") == "Japan's")
        #expect(possessive("United States") == "United States'")
        let states = chartCountryChoices(CountryRow(id: "us", code: "us", name: "United States"), charts: charts(country: "jp"), name: name)
        #expect(states[2].title == "Also add United States' playlists")
    }
}

import Foundation

/// One row of a country picker: what picking it answers (`id`, a country's code, or a choice such
/// as the listener's own charts), the words it shows, the code a search also matches, and a quiet
/// word after the name.
public struct CountryRow: Identifiable, Hashable, Sendable {
    public var id: String
    public var code: String
    public var name: String
    public var detail: String?

    public init(id: String, code: String, name: String, detail: String? = nil) {
        self.id = id
        self.code = code
        self.name = name
        self.detail = detail
    }
}

/// The row of the Charts page's country menu that goes back to the listener's own charts.
public let chartCountryYours = "yours"

/// The Charts page's country menu: at the top the listener's own charts, "Yours (Brazil)", and
/// while they chose a country of their own, "Same as the server (United States)"; then every
/// country Apple Music has charts for, by name, the listener's own left out and pinned ones marked.
public struct ChartCountryMenu: Equatable, Sendable {
    public var top: [CountryRow]
    public var countries: [CountryRow]

    public init(top: [CountryRow], countries: [CountryRow]) {
        self.top = top
        self.countries = countries
    }
}

public func chartCountryMenu(_ charts: ChartChoices, name: (String) -> String) -> ChartCountryMenu {
    let own = charts.country.flatMap { $0.isEmpty ? nil : $0 }
    var top = [CountryRow(id: chartCountryYours, code: own ?? "", name: own.map { "Yours (\(name($0)))" } ?? "Yours")]
    if charts.chosen != nil {
        let server = charts.serverCountry.flatMap { $0.isEmpty ? nil : $0 }
        top.append(CountryRow(id: chartCountryAuto, code: server ?? "",
                              name: server.map { "Same as the server (\(name($0)))" } ?? "Same as the server"))
    }
    let countries = chartCountryOrder(charts.countries, own: nil, pinned: [], name: name)
        .filter { $0.caseInsensitiveCompare(own ?? "") != .orderedSame }
        .map { CountryRow(id: $0, code: $0, name: name($0), detail: charts.isPinned($0) ? "Pinned" : nil) }
    return ChartCountryMenu(top: top, countries: countries)
}

/// What picking a country in the Charts page's menu can do: only look at its charts here, make it
/// the listener's chart country, or add or remove its chart playlists beside their own.
public enum ChartCountryAction: String, Sendable {
    case look, use, pin, unpin
}

/// One answer of the choice that opens when a country is picked, with the words under it and
/// whether it can be picked now.
public struct ChartCountryChoice: Identifiable, Equatable, Sendable {
    public var action: ChartCountryAction
    public var title: String
    public var detail: String?
    public var enabled: Bool

    public var id: String { action.rawValue }

    public init(action: ChartCountryAction, title: String, detail: String? = nil, enabled: Bool = true) {
        self.action = action
        self.title = title
        self.detail = detail
        self.enabled = enabled
    }
}

/// A name with its possessive ending: "Japan's", "United States'".
public func possessive(_ name: String) -> String { name.hasSuffix("s") ? "\(name)'" : "\(name)'s" }

/// The choices for a row picked in the country menu. "Same as the server" offers only looking at
/// the server's country and following it again; any other country also offers adding its chart
/// playlists, or removing them while it is pinned, which can't be picked once every pin is taken.
public func chartCountryChoices(_ row: CountryRow, charts: ChartChoices, name: (String) -> String) -> [ChartCountryChoice] {
    let look = ChartCountryChoice(action: .look, title: "Just look here", detail: "Nothing is saved", enabled: !row.code.isEmpty)
    if row.id == chartCountryAuto {
        return [look, ChartCountryChoice(action: .use, title: "Use the server's country for my charts",
                                         detail: "Your chart playlists and Home follow the server's country, in every app")]
    }
    let shown = name(row.code)
    let use = ChartCountryChoice(action: .use, title: "Use \(shown) for my charts",
                                 detail: "Your chart playlists and Home switch to \(shown), in every app")
    if charts.isPinned(row.code) {
        return [look, use, ChartCountryChoice(action: .unpin, title: "Remove \(possessive(shown)) playlists")]
    }
    if charts.pinsFull {
        return [look, use, ChartCountryChoice(action: .pin, title: "Also add \(possessive(shown)) playlists",
                                              detail: "\(charts.maxPinned) countries already have playlists", enabled: false)]
    }
    return [look, use, ChartCountryChoice(action: .pin, title: "Also add \(possessive(shown)) playlists",
                                          detail: "Keeps yours and adds Popular right now · \(shown) and the rest")]
}

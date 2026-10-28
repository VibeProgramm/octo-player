import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// One chart the server's country has. `label` is the chip's word ("Top songs", "Hip-Hop/Rap"),
/// `name` the playlist's ("Top Hip-Hop/Rap"), `kind` overall, new, trending or genre, `on`
/// whether it is a playlist for everyone, and `playlist` this listener's playlist id while it is
/// one.
public struct ChartChoice: Decodable, Sendable, Equatable, Hashable, Identifiable {
    public var id: String
    public var name: String
    public var label: String
    public var kind: String
    public var playlist: String?
    public var on: Bool

    enum CodingKeys: String, CodingKey { case id, name, label, kind, playlist, on }

    public init(id: String, name: String = "", label: String = "", kind: String = "", playlist: String? = nil, on: Bool = false) {
        self.id = id
        self.name = name
        self.label = label
        self.kind = kind
        self.playlist = playlist
        self.on = on
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.string(.id) ?? ""
        name = c.string(.name) ?? ""
        label = c.string(.label) ?? ""
        kind = c.string(.kind) ?? ""
        playlist = c.string(.playlist)
        on = c.bool(.on) ?? false
    }
}

/// The charts a country has, Popular right now first, and the listener's own choices: `country` is
/// the country these charts are for, `setting` the listener's own chart country (`auto` while they
/// follow the server), `pinned` the countries whose charts they get as playlists too, `countries`
/// every country Apple Music has charts for, `maxPinned` how many may be pinned and
/// `serverCountry` the server's own. Servers before octoTopSongs version 3 send only the country
/// and the charts.
public struct ChartChoices: Decodable, Sendable, Equatable {
    public var country: String?
    public var setting: String?
    public var pinned: [String]
    public var countries: [String]
    public var maxPinned: Int
    public var serverCountry: String?
    public var chart: [ChartChoice]

    enum CodingKeys: String, CodingKey { case country, setting, pinned, countries, maxPinned, serverCountry, chart }

    public init(country: String? = nil, setting: String? = nil, pinned: [String] = [], countries: [String] = [],
                maxPinned: Int = 0, serverCountry: String? = nil, chart: [ChartChoice] = []) {
        self.country = country
        self.setting = setting
        self.pinned = pinned
        self.countries = countries
        self.maxPinned = maxPinned
        self.serverCountry = serverCountry
        self.chart = chart
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        country = c.string(.country)
        setting = c.string(.setting)
        pinned = (try? c.decodeIfPresent([String].self, forKey: .pinned)) ?? []
        countries = (try? c.decodeIfPresent([String].self, forKey: .countries)) ?? []
        maxPinned = c.int(.maxPinned) ?? 0
        serverCountry = c.string(.serverCountry)
        chart = (try? c.decodeIfPresent([ChartChoice].self, forKey: .chart)) ?? []
    }

    /// The listener's own chart country when they chose one; nil while they follow the server.
    public var chosen: String? {
        guard let setting, !setting.isEmpty, setting.lowercased() != chartCountryAuto else { return nil }
        return setting
    }

    /// Whether `code` is one of the listener's pinned countries.
    public func isPinned(_ code: String) -> Bool { pinned.contains { $0.caseInsensitiveCompare(code) == .orderedSame } }

    /// Whether every pin is taken. A server that does not say how many may be pinned decides itself.
    public var pinsFull: Bool { maxPinned > 0 && pinned.count >= maxPinned }
}

extension TopSong {
    /// A ranked entry made here rather than read from the server.
    public init(rank: Int, plays: Int? = nil, inLibrary: Bool = false, song: Song?) {
        self.rank = rank
        self.plays = plays
        self.inLibrary = inLibrary
        self.song = song
    }
}

extension SubsonicClient {
    /// The charts a country has, `country` nil for the listener's own. Only for servers that list
    /// octoTopSongs version 2; a country only on version 3.
    public func charts(country: String? = nil) async throws -> ChartChoices {
        var params: [(String, String)] = []
        if let country { params.append(("country", country)) }
        return try await get("getCharts", params, key: "charts")
    }

    /// Saves the listener's own chart country (`auto` follows the server) and answers the
    /// listener's charts. An answer without them throws.
    public func setChartCountry(_ country: String) async throws -> ChartChoices {
        try await get("setChartCountry", [("country", country)], key: "charts")
    }

    /// Pins one country, or unpins it, on the server's own list of the listener's pins, and
    /// answers the listener's charts. An answer without them throws.
    public func pinChartCountry(_ country: String, pinned: Bool) async throws -> ChartChoices {
        try await get("pinChartCountry", [("country", country), ("pinned", pinned ? "true" : "false")], key: "charts")
    }
}

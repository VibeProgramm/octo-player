import OctoKit
import SwiftUI

/// What to tell someone when a read from the server fails.
func onlineFailureWords(_ error: Error) -> String { userMessage(for: error) ?? "Something went wrong." }

/// Runs a read from the server. Nothing back means no server is signed in; a read that was
/// canceled gives nil, and the page keeps what it had.
@MainActor
func loadOnline<T>(_ read: () async throws -> T?) async -> LoadState<T>? {
    do {
        guard let value = try await read() else { return .failed("Sign in to your server to see this.") }
        return .ready(value)
    } catch {
        guard let words = userMessage(for: error) else { return nil }
        return .failed(words)
    }
}

// MARK: Online album

/// An album on the server that is not in the library. Songs the library has play as usual; the
/// rest can be added one by one or all at once.
struct OnlineAlbumView: View {
    let id: String
    let name: String
    @Environment(Discovery.self) private var discovery
    @Environment(Downloads.self) private var downloads
    @Environment(Player.self) private var player
    @State private var page: LoadState<OnlineAlbumPage> = .loading
    /// Set once the album is asked for here, cleared if the server says no.
    @State private var asked = false
    @State private var highlight: String?
    @State private var arrivalWait: Task<Void, Never>?

    var body: some View {
        LoadStateView(state: page, retry: { Task { await reload() } }) { found in
            let songs = found.songs.map(\.asSong)
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    header(found.album, songs, lengthsMs: found.songs.map(\.durationMs))
                    let mixed = songs.contains(where: \.isExternal)
                    ForEach(Array(songs.enumerated()), id: \.element.id) { index, song in
                        // Only say who is singing when it is not the album's artist.
                        SongRow(song: song, list: songs, position: index, showsCover: false, number: index + 1,
                                describe: { $0.byline != found.album.artist ? $0.byline : "" },
                                highlight: song.id == highlight, ownership: mixed) { player.play(songs, from: index) }
                    }
                }
            }
            .barClearance()
        }
        .pageArtwork(.onlineAlbum(id: id, name: name), coverId(of: page.value?.album.artwork))
        .task { if page.value == nil { await reload() } }
        .task(id: page.value?.album.id) { await markHighlight() }
        .onChange(of: downloads.arrived) { _, _ in
            // A song from here that joins the library turns into a library song on the page,
            // once its check has landed. A later arrival starts the wait again.
            arrivalWait?.cancel()
            arrivalWait = Task {
                try? await Task.sleep(for: .milliseconds(checkHoldMs + swapFadeDurationMs))
                guard !Task.isCancelled else { return }
                let songs = (page.value?.songs ?? []).map(\.asSong)
                if songs.contains(where: { $0.isExternal && downloads.state($0.id) == .done }) { await refresh() }
            }
        }
    }

    /// Whether the album is on its way: asked for here, or every song from it that is not in the
    /// library already asked for.
    private func downloading(_ songs: [Song]) -> Bool {
        let finds = songs.filter(\.isExternal)
        return asked || (!finds.isEmpty && finds.allSatisfy { downloads.state($0.id) != .none })
    }

    private func header(_ album: OnlineAlbum, _ songs: [Song], lengthsMs: [Int64]) -> some View {
        // The length only adds up when every song's length is known.
        let length = !lengthsMs.isEmpty && lengthsMs.allSatisfy { $0 > 0 } ? Int(lengthsMs.reduce(0, +) / 1_000).spokenLength : nil
        let details = [album.year.map(String.init), album.songCount == 1 ? "1 song" : "\(album.songCount) songs", length]
            .compactMap { $0 }.joined(separator: " • ")
        let artistId = album.artistId.flatMap { $0.isEmpty ? nil : $0 }
        return VStack(alignment: .leading, spacing: 0) {
            VStack(spacing: 0) {
                Cover(id: album.artwork, size: 240, radius: 10)
                    .shadow(color: .black.opacity(0.4), radius: 16, y: 8)
                Text(album.title)
                    .font(.octoTitle)
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .padding(.top, 20)
                if let artistId {
                    NavigationLink(value: Route.onlineArtist(id: artistId, name: album.artist)) {
                        Text(album.artist).font(.octoBody).foregroundStyle(OctoColor.accent)
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 4)
                } else {
                    Text(album.artist).font(.octoBody).foregroundStyle(OctoColor.textSecondary).padding(.top, 4)
                }
                HStack {
                    Text(details)
                        .font(.octoCaption)
                        .foregroundStyle(OctoColor.textMuted)
                        .lineLimit(2)
                    Spacer()
                }
                .frame(minHeight: 44)
                .padding(.top, 14)
                PlayButtons(songs: songs).padding(.top, 4)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 20)
            // The cover stays clean. The plus adds the album to the library; for an album partly
            // there, a note says how much of it is.
            if songs.contains(where: \.isExternal) {
                let busy = downloading(songs)
                HStack(spacing: 0) {
                    Button { add(album, songs) } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "plus.circle")
                                .font(.system(size: 17))
                            Text(busy ? "Adding" : "Add to your library")
                                .font(DiscoveryStyle.label)
                                .lineLimit(1)
                        }
                        .foregroundStyle(OctoColor.textSecondary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 12)
                        .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .disabled(busy)
                    .opacity(busy ? 0.6 : 1)
                    if let note = albumLibraryNote(songs: songs.count,
                                                   outside: songs.filter { isOutsideLibrary($0, adopted: downloads.adopted) }.count) {
                        Spacer(minLength: 0)
                        Text(note)
                            .font(.octoCaption)
                            .foregroundStyle(OctoColor.textMuted)
                            .lineLimit(1)
                            .padding(.horizontal, 10)
                    }
                }
                .padding(.horizontal, 10)
            }
        }
        .padding(.top, detailTopGap)
        .padding(.bottom, 12)
    }

    /// Has the server download the whole album into the library.
    private func add(_ album: OnlineAlbum, _ songs: [Song]) {
        asked = true
        Task {
            if !(await downloads.requestAlbum(id, songs: songs, title: album.title)) { asked = false }
        }
    }

    private func reload() async {
        page = .loading
        if let state = await loadOnline({ try await discovery.album(id) }) { page = state }
    }

    /// Asks the server again, keeping the page as it is if it cannot answer.
    private func refresh() async {
        let fresh = await loadOnline { try await discovery.album(id) }
        if let fresh, case .ready = fresh { page = fresh }
    }

    /// The album's main song: the one first in the artist's top songs on the server.
    private func markHighlight() async {
        guard let found = page.value else { return }
        let ranked = await discovery.artistTopRefs(found.album.artist)
        highlight = albumHighlight(found.songs.map(\.asSong).map { ($0.id, $0.title, $0.byline) }, ranked)
    }
}

// MARK: Online artist

/// An artist on the server that is not in the library, with their albums.
struct OnlineArtistView: View {
    let id: String
    let name: String
    @Environment(Discovery.self) private var discovery
    @State private var page: LoadState<OnlineArtistPage> = .loading
    @State private var width: CGFloat = 390

    var body: some View {
        LoadStateView(state: page, retry: { Task { await reload() } }) { found in
            let artist = found.artist
            let columns = max(1, Int((width - 40 + 14) / (150 + 14)))
            let cell = (width - 40 - 14 * CGFloat(columns - 1)) / CGFloat(columns)
            ScrollView {
                VStack(spacing: 0) {
                    // The picture stays clean; the line under the name says it.
                    Cover(id: artist.artwork, size: 160, radius: 80)
                    Text(artist.name)
                        .font(.octoTitle)
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                        .padding(.top, 16)
                    Text("\(found.albums.count == 1 ? "1 album" : "\(found.albums.count) albums") • \(notInLibraryText)")
                        .font(.octoCaption)
                        .foregroundStyle(OctoColor.textMuted)
                        .padding(.top, 4)
                }
                .frame(maxWidth: .infinity)
                .padding(.top, detailTopGap)
                .padding(.bottom, 8 + 18)
                LazyVGrid(columns: Array(repeating: GridItem(.fixed(cell), spacing: 14, alignment: .top), count: columns),
                          alignment: .leading, spacing: 18) {
                    ForEach(found.albums) { album in
                        OnlineAlbumCard(album: album, width: cell)
                    }
                }
                .padding(.horizontal, 20)
            }
            .barClearance()
        }
        .pageArtwork(.onlineArtist(id: id, name: name), coverId(of: page.value?.artist.artwork))
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
        .task { if page.value == nil { await reload() } }
    }

    private func reload() async {
        page = .loading
        if let state = await loadOnline({ try await discovery.artist(id) }) { page = state }
    }
}

// MARK: Charts

/// The charts and new songs an Octo server reads from Apple Music, one at a time, picked with the
/// chips: the chart country's most played songs, Best New Songs, Trending Songs and a chart per
/// genre. A song the library has plays from it; any other has the add button at the end of its
/// row. On a server that has every country's charts, the country button beside the title shows
/// another country's; while one is shown, it can become the listener's own or be pinned, so its
/// charts come as playlists too.
struct ChartsView: View {
    @Environment(Discovery.self) private var discovery
    @Environment(Player.self) private var player
    @Environment(Router.self) private var router
    @ScaledMetric(relativeTo: .largeTitle) private var titleSize: CGFloat = 32
    @State private var choices: [ChartChoice] = []
    @State private var selected = Charts.overall
    /// The chosen chart's songs, or nil while they are on their way.
    @State private var list: ChartList<Song>?
    /// Whether the server answered with nothing it can show.
    @State private var none = false
    @State private var loading: Task<Void, Never>?
    /// The listener's own charts and choices, as the server last said.
    @State private var own: ChartChoices?
    /// Whether the server has every country's charts and saves the listener's own.
    @State private var countriesOn = false
    /// The country shown in place of the listener's own; nil shows their own.
    @State private var browsing: String?
    @State private var switching: Task<Void, Never>?
    /// The country on its way while `switching`; nil for the listener's own.
    @State private var target: String?
    @State private var picking = false
    @State private var savingCountry = false
    @State private var savingPin = false

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                // The title keeps its whole width; a long country name gives way in the button.
                HStack(alignment: .chartsTitleLine, spacing: 0) {
                    ScreenTitle("Charts")
                        .fixedSize(horizontal: true, vertical: false)
                        .alignmentGuide(.chartsTitleLine) { d in d[.firstTextBaseline] - titleCapHeight / 2 }
                    Spacer(minLength: 0)
                    if canPickCountry {
                        Button { picking = true } label: { ChoiceChip(text: shownCountryName, systemImage: OctoIcon.globe.symbol) }
                            .buttonStyle(.plain)
                            .alignmentGuide(.chartsTitleLine) { d in d[VerticalAlignment.center] }
                            .padding(.trailing, 20)
                            .accessibilityLabel("Chart country: \(shownCountryName)")
                    }
                }
                if !choices.isEmpty {
                    ScrollView(.horizontal) {
                        HStack(spacing: 8) {
                            ForEach(choices) { choice in
                                OnOffChip(text: choice.label, on: choice.id == selected) { pick(choice.id) }
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                    .scrollIndicators(.hidden)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 4)
                    .padding(.bottom, 12)
                }
                if let browsing, !none {
                    browsingRow(browsing)
                }
                if none {
                    quiet("This server doesn't have charts.")
                } else if let list {
                    if list.songs.isEmpty {
                        quiet("Nothing in this chart right now. Try another.")
                    } else {
                        if let caption = chartCaption(source: list.source, chart: list.chart, country: list.country) {
                            quiet(caption)
                        }
                        GlazeButton("Play all", size: .small) { play(list, 0) }
                        .padding(.leading, 20)
                        .padding(.bottom, 8)
                        let all = list.songs.map(\.track)
                        ForEach(Array(list.songs.enumerated()), id: \.element.track.id) { index, ranked in
                            SongRow(song: ranked.track, list: all, position: index, offersAdd: true, addSpace: true, rank: ranked.rank,
                                    describe: { shown in
                                        [shown.byline.isEmpty ? nil : shown.byline, rowAlbum(shown).isEmpty ? nil : rowAlbum(shown),
                                         ranked.plays.map(playsText)].compactMap { $0 }.joined(separator: " • ")
                                    }) { play(list, index) }
                                .id("chart:\(list.chart):\(ranked.track.id)")
                        }
                    }
                } else {
                    ProgressView()
                        .tint(OctoColor.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 24)
                }
            }
            .padding(.top, detailTopGap)
        }
        .barClearance()
        .sheet(isPresented: $picking) {
            CountryPickerSheet(sections: countrySections, chosen: (switching == nil ? browsing : target) ?? own?.chosen ?? chartCountryAuto) {
                browse($0)
            }
        }
        .task {
            guard choices.isEmpty, list == nil else { return }
            load()
            // The kept copy shows until the server's own answer lands.
            let kept = discovery.keptChartChoices()
            if let kept { showOwn(kept) }
            countriesOn = await discovery.offersChartCountries()
            let asking = chartChoicesOnOpen(countries: countriesOn, kept: kept != nil)
            if asking.ask, let answer = try? await discovery.chartChoices(fresh: asking.fresh) { showOwn(answer) }
            if choices.isEmpty, !(await discovery.offersCharts()) { none = true }
        }
    }

    // MARK: The country

    /// The title's cap height, for the country button to sit on the middle of its capitals.
    private var titleCapHeight: CGFloat { UIFont.systemFont(ofSize: titleSize, weight: .black).capHeight }

    private var canPickCountry: Bool { countriesOn && !none && !(own?.countries.isEmpty ?? true) }

    /// The country whose charts show.
    private var shownCode: String? { browsing ?? own?.country ?? list?.country }

    private var shownCountryName: String { shownCode.map { countryName($0) } ?? "Country" }

    /// The picker's rows: following the server's country, the listener's own when they chose one,
    /// their pinned ones, then every other country by name.
    private var countrySections: [CountrySection] {
        guard let own else { return [] }
        func row(_ code: String) -> CountryRow { CountryRow(id: code, code: code, name: countryName(code)) }
        let server = own.serverCountry.flatMap { $0.isEmpty ? nil : $0 }
        let follow = CountryRow(id: chartCountryAuto, code: server ?? "",
                                name: server.map { "Same as the server (\(countryName($0)))" } ?? "Same as the server")
        let mine = own.chosen
        let order = chartCountryOrder(own.countries, own: mine ?? own.country, pinned: own.pinned) { countryName($0) }
        let taken = Set(([mine ?? own.country].compactMap { $0 } + own.pinned).map { $0.lowercased() })
        var sections = [CountrySection(title: nil, rows: [follow])]
        if let mine { sections.append(CountrySection(title: "Yours", rows: [row(mine)])) }
        if !own.pinned.isEmpty { sections.append(CountrySection(title: "Pinned", rows: own.pinned.map(row))) }
        sections.append(CountrySection(title: "All countries", rows: order.filter { !taken.contains($0.lowercased()) }.map(row)))
        return sections
    }

    /// Under the chips while another country shows: make it the listener's own, or pin it.
    private func browsingRow(_ code: String) -> some View {
        let pinned = own?.isPinned(code) ?? false
        let full = !pinned && (own?.pinsFull ?? false)
        return HStack(spacing: 8) {
            GlazeButton("Use as my country", size: .small, enabled: !savingPin, loading: savingCountry) { save(code) }
            GlazeButton(pinned ? "Unpin" : "Pin", icon: pinned ? .pinned : .pin, size: .small,
                        enabled: !full && !savingCountry, loading: savingPin) { togglePin(code, pinned: !pinned) }
            if full, let most = own?.maxPinned {
                Text("\(most) pinned")
                    .font(.octoCaption)
                    .foregroundStyle(OctoColor.textMuted)
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 12)
    }

    /// The listener's own charts as the server said: their chips show unless another country does.
    private func showOwn(_ answer: ChartChoices) {
        let before = own?.country
        own = answer
        guard browsing == nil else { return }
        showChoices(answer.chart, reload: before != nil && before != answer.country)
    }

    /// The chips of the country shown; a chart it lacks gives way to Popular right now.
    private func showChoices(_ chips: [ChartChoice], reload: Bool) {
        choices = chips
        if !chips.isEmpty, !chips.contains(where: { $0.id == selected }) {
            selected = Charts.overall
            load()
        } else if reload {
            load()
        }
    }

    /// Shows a country's charts, the listener's own as their own; following the server's saves that.
    private func browse(_ id: String) {
        if id == chartCountryAuto {
            if own?.chosen == nil { show(nil) } else { save(chartCountryAuto) }
            return
        }
        show(id.caseInsensitiveCompare(own?.country ?? "") == .orderedSame ? nil : id)
    }

    /// Shows a country's charts, nil for the listener's own. A country picked while another is on
    /// its way replaces that one.
    private func show(_ country: String?) {
        guard country != (switching == nil ? browsing : target) else { return }
        switching?.cancel()
        loading?.cancel()
        list = nil
        target = country
        switching = Task {
            do {
                let got = try await discovery.chartChoices(country: country)
                guard !Task.isCancelled else { return }
                switching = nil
                target = nil
                browsing = country
                choices = got?.chart ?? []
                if !choices.contains(where: { $0.id == selected }) { selected = Charts.overall }
                load()
            } catch {
                guard !Task.isCancelled else { return }
                switching = nil
                target = nil
                router.show(serverWords(error) ?? "Couldn't show that country's charts. Try again.")
                load()
            }
        }
    }

    /// Saves a country as the listener's own (`auto` follows the server), then shows their own.
    private func save(_ code: String) {
        guard !savingCountry else { return }
        savingCountry = true
        Task {
            defer { savingCountry = false }
            do {
                guard let answer = try await discovery.saveChartCountry(code) else { return }
                own = answer
                switching?.cancel()
                switching = nil
                target = nil
                browsing = nil
                showChoices(answer.chart, reload: true)
            } catch {
                router.show(serverWords(error) ?? "Couldn't save. Try again.")
            }
        }
    }

    private func togglePin(_ code: String, pinned: Bool) {
        guard !savingPin else { return }
        savingPin = true
        Task {
            defer { savingPin = false }
            do {
                if let answer = try await discovery.pinChartCountry(code, pinned: pinned) { own = answer }
            } catch {
                router.show(serverWords(error) ?? "Couldn't save. Try again.")
            }
        }
    }

    /// What the server said when it refused, such as too many countries in an hour.
    private func serverWords(_ error: Error) -> String? {
        guard case .server(_, let message)? = error as? OctoError else { return nil }
        let words = message.trimmingCharacters(in: .whitespacesAndNewlines)
        return words.isEmpty ? nil : words
    }

    private func quiet(_ text: String) -> some View {
        Text(text)
            .font(.octoCaption)
            .foregroundStyle(OctoColor.textMuted)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
    }

    private func pick(_ chart: String) {
        if chart == selected { return }
        selected = chart
        load()
    }

    private func load() {
        loading?.cancel()
        list = nil
        let chart = selected
        let country = browsing
        loading = Task {
            let got = await discovery.chart(chart, country: country)
            guard !Task.isCancelled, chart == selected, country == browsing else { return }
            list = got ?? ChartList(chart: chart, name: nil, source: "", country: country, songs: [])
        }
    }

    private func play(_ list: ChartList<Song>, _ index: Int) {
        player.play(list.songs.map(\.track), from: max(0, index))
    }
}

extension VerticalAlignment {
    private enum ChartsTitleLine: AlignmentID {
        static func defaultValue(in dimensions: ViewDimensions) -> CGFloat { dimensions[VerticalAlignment.center] }
    }

    /// The middle of the Charts title's capitals, for the country button beside it.
    fileprivate static let chartsTitleLine = VerticalAlignment(ChartsTitleLine.self)
}

import OctoKit
import SwiftUI

/// One row of a country picker: what picking it answers (`id`, a country's code, or a choice such
/// as following the server's country), the words it shows, and the code a search also matches.
struct CountryRow: Identifiable, Hashable {
    let id: String
    let code: String
    let name: String
}

/// A group of rows under its heading; a picker with one group needs no heading.
struct CountrySection: Identifiable {
    let title: String?
    let rows: [CountryRow]

    var id: String { title ?? "" }
}

/// A country picked on a sheet: Apple's search bar over the countries, which matches a name with
/// case and accents ignored or a two-letter code exactly, and the chosen row checked. Picking a
/// row closes the sheet.
struct CountryPickerSheet: View {
    var title = "Country"
    let sections: [CountrySection]
    let chosen: String?
    let pick: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var typed = ""

    /// The groups with only the rows what was typed picks; a group left with none goes.
    private var shown: [CountrySection] {
        sections
            .map { section in
                CountrySection(title: section.title, rows: section.rows.filter { countryMatches(name: $0.name, code: $0.code, typed: typed) })
            }
            .filter { !$0.rows.isEmpty }
    }

    var body: some View {
        NavigationStack {
            List {
                ForEach(shown) { section in
                    Section {
                        ForEach(section.rows) { row in
                            Button {
                                dismiss()
                                pick(row.id)
                            } label: {
                                HStack(spacing: 8) {
                                    Text(row.name)
                                        .foregroundStyle(.primary)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                    if row.id == chosen {
                                        Image(systemName: "checkmark")
                                            .fontWeight(.semibold)
                                            .foregroundStyle(OctoColor.accent)
                                            .accessibilityHidden(true)
                                    }
                                }
                                .contentShape(Rectangle())
                            }
                            .accessibilityAddTraits(row.id == chosen ? .isSelected : [])
                        }
                    } header: {
                        if let heading = section.title { Text(heading) }
                    }
                }
            }
            .overlay {
                if shown.isEmpty { ContentUnavailableView.search(text: typed) }
            }
            .searchable(text: $typed, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search countries")
            .autocorrectionDisabled()
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}

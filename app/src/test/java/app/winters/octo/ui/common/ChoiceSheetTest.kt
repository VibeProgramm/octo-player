package app.winters.octo.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

// The answers a question's search field leaves, as places in the full list.
class ChoiceSheetTest {
    private val countries = listOf("Brazil" to "br", "Côte d'Ivoire" to "ci", "Japan" to "jp", "South Korea" to "kr", "Spain" to "es")

    private fun request(searchHint: String? = COUNTRY_SEARCH_HINT, match: ((Choice, String) -> Boolean)? = ::countryChoiceMatches) =
        ChoiceRequest("Country", countries.map { Choice(it.first, key = it.second) }, selected = 2, onPick = {}, searchHint = searchHint, match = match)

    @Test
    fun nothingTypedShowsEveryAnswer() {
        assertEquals(listOf(0, 1, 2, 3, 4), shownChoices(request(), ""))
        assertEquals(listOf(0, 1, 2, 3, 4), shownChoices(request(), "   "))
    }

    @Test
    fun theAnswersLeftKeepTheirOrderAndPlaces() {
        assertEquals(listOf(3, 4), shownChoices(request(), "s"))
        assertEquals(listOf(1), shownChoices(request(), "cote"))
    }

    @Test
    fun aCodeFindsItsCountryOnlyWhole() {
        assertEquals(listOf(2), shownChoices(request(), "JP"))
        assertEquals(listOf(4), shownChoices(request(), "es"))
        // Spain's code starts with an e, but only names hold one.
        assertEquals(listOf(1, 3), shownChoices(request(), "e"))
    }

    @Test
    fun withoutItsOwnRuleTheWordsAreInsideTheName() {
        assertEquals(listOf(0, 2, 3, 4), shownChoices(request(match = null), "A"))
        assertEquals(emptyList<Int>(), shownChoices(request(match = null), "jp"))
    }

    @Test
    fun aQuestionWithoutASearchShowsEveryAnswer() {
        assertEquals(listOf(0, 1, 2, 3, 4), shownChoices(request(searchHint = null), "japan"))
    }
}

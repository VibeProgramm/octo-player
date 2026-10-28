package app.winters.octo.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Sizes the album, artist, genre and folder pages share, beside the
// frame's own in DesktopMetrics.
object PageSize {
    // The picture at the top of an album, artist or genre page, and on a
    // page narrower than HeaderNarrow, where the heading's buttons need
    // the room.
    val HeaderArt = 180.dp
    val HeaderArtSmall = 128.dp
    val HeaderNarrow = 720.dp
    // How wide a page is at least for an artist's biography to sit beside
    // their name in the header, rather than under it.
    val AsideFrom = 1000.dp
    // The widest a control line's place button grows (Live radio); a
    // longer place shortens inside it.
    val PlaceButton = 260.dp
    // The widest a choice button on a control line grows (a chart, a
    // finding); longer words shorten inside it.
    val ChoiceButton = 260.dp
    // The widest a running fix's words grow on a control line.
    val RunningWords = 320.dp
    // The narrowest a card may be in a grid that fills the page's width.
    val Card = 168.dp
    // The same for an artist's round card beside a genre's albums, smaller
    // so a row of them stays a row.
    val ArtistCard = 128.dp
    // A genre's line in the list of genres at its narrowest, and its covers.
    val GenreLine = 280.dp
    val Mosaic = 48.dp
    // A folder's cover in its line.
    val FolderCover = 28.dp
    // The widest a biography runs, so its lines stay easy to read.
    val Reading = 720.dp
    // A live list's name while it is edited.
    val NameField = 420.dp
}

// An artist's first letter, in place of a picture the server does not have.
object PageType {
    val monogram = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold)
}

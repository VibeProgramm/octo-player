package app.winters.octo.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoIcons
import app.winters.octo.design.OctoInk
import app.winters.octo.design.OctoType

// What a dropdown button takes beside its words, in dp: its 10dp ends, the
// chevron and the room before it, and the icon and its room when it has one.
const val DROPDOWN_CHROME_DP = 10 + 6 + 12 + 10
const val DROPDOWN_ICON_DP = 16 + 6

// A choice on a control line as a quiet capsule that opens its list: an
// optional icon, the choice in force, whole, and a small chevron saying it
// opens. With `showLabel` false it is the icon and chevron alone, the words
// kept for a screen reader as `description`. It is dimmed and out of reach
// while not `enabled`. A tap asks through the choice sheet, which opens the
// list beside it. Pages use it in place of a row of chips that would scroll.
@Composable
fun HeaderDropdown(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    showLabel: Boolean = true,
    description: String = label,
    enabled: Boolean = true,
    clickLabel: String? = null,
) {
    val sheet = LocalChoiceSheet.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ink = if (pressed) OctoColors.TextPrimary else OctoColors.TextSecondary
    Row(
        modifier
            .height(32.dp)
            .alpha(if (enabled) 1f else OctoInk.DisabledAlpha)
            .choiceAnchor(sheet)
            .clip(CircleShape)
            .background(OctoColors.TextPrimary.copy(alpha = if (pressed) 0.12f else 0.08f))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        // Held short on a narrow line, the words end in an ellipsis; the
        // whole choice is still read out.
        if (showLabel) {
            Text(label, style = OctoType.label, color = ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        Icon(painterResource(OctoIcons.Chevron), contentDescription = null, tint = OctoColors.TextMuted, modifier = Modifier.size(12.dp).rotate(90f))
    }
}

// One list of choices behind a dropdown button, the one in force checked.
// Picking another hands its place in `labels` to `onPick`.
fun showDropdown(sheet: ChoiceSheet, title: String, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    sheet.show(ChoiceRequest(title, labels.map { Choice(it) }, selected = selected, onPick = onPick))
}

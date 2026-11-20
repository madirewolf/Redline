package com.redline.app.ui.community

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.redline.app.community.Leader
import com.redline.app.community.barFractions
import com.redline.app.ui.theme.Tabular
import kotlinx.coroutines.delay

/**
 * A round badge with someone's initials. Yours is red; everyone else's is grey
 * or blue. Decorative: the name always sits beside it, so screen readers skip it.
 */
@Composable
internal fun Avatar(
    name: String,
    seed: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    isMe: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val (background, content) = when {
        isMe -> colors.primaryContainer to colors.onPrimaryContainer
        (seed.hashCode() and 1) == 0 -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials(name),
            style = if (size >= 56.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
            color = content
        )
    }
}

/** A small rounded chip you can turn on and off, styled like the nav pill's selected tab. */
@Composable
internal fun ToggleChip(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (checked) colors.primaryContainer else colors.surfaceContainerHigh)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (checked) colors.onPrimaryContainer else colors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (checked) colors.onPrimaryContainer else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** One of a set of chips where exactly one is picked. */
@Composable
internal fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
        )
    }
}

/** A small rounded label, like a gym's tags. */
@Composable
internal fun TagChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** A coloured dot and label on a tinted pill, the same look as Progress's status chips. */
@Composable
internal fun DotChip(text: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** A count in a red circle, for unread messages. */
@Composable
internal fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(18.dp)
            .widthIn(min = 18.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall.merge(Tabular),
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/**
 * Two bars, yours above theirs, scaled to whoever is ahead. Yours is red,
 * theirs blue, and the leader's number is bold.
 */
@Composable
internal fun CompareBars(
    label: String,
    theirName: String,
    mine: Double,
    theirs: Double,
    mineText: String,
    theirsText: String,
    leader: Leader?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val (mineFraction, theirFraction) = barFractions(mine, theirs)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$label: you $mineText, $theirName $theirsText" }
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        BarRow("You", mineFraction, mineText, colors.primary, bold = leader == Leader.YOU)
        Spacer(modifier = Modifier.height(6.dp))
        BarRow(theirName, theirFraction, theirsText, colors.tertiary, bold = leader == Leader.THEM)
    }
}

@Composable
private fun BarRow(who: String, fraction: Float, value: String, color: Color, bold: Boolean) {
    val colors = MaterialTheme.colorScheme
    val shown by animateFloatAsState(fraction, animationSpec = tween(600), label = "bar")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = who,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(52.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.surfaceContainerHigh)
        ) {
            if (shown > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(shown.coerceIn(0.02f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(color)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.merge(Tabular),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 64.dp)
        )
    }
}

/** The current time, moved on once a minute so "5m" becomes "6m" while you look. */
@Composable
internal fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - now % 60_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

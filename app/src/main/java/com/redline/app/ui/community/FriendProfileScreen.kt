package com.redline.app.ui.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redline.app.community.Member
import com.redline.app.ui.components.EmptyState
import com.redline.app.ui.components.RedlineTopBar
import com.redline.app.ui.components.ScreenPadding
import com.redline.app.ui.components.SectionCard

@Composable
fun FriendProfileScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    viewModel: FriendProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val member = state.member

    Column(modifier = Modifier.fillMaxSize()) {
        RedlineTopBar(title = member?.name.orEmpty(), onBack = onBack)
        when {
            state.isLoading -> Unit
            member == null -> EmptyState(
                icon = Icons.Outlined.PersonOff,
                title = "Profile not found",
                body = "This person isn't in your community any more.",
                modifier = Modifier.padding(top = 48.dp)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "header") {
                    ProfileHeader(
                        member = member,
                        onMessage = { viewModel.openChat(onOpenChat) },
                        onAddFriend = viewModel::addFriend
                    )
                }
                item(key = "stats") { StatsCard(state, member) }
                item(key = "lifts") { LiftsCard(state, member) }
            }
        }
    }
}

@Composable
private fun ProfileHeader(member: Member, onMessage: () -> Unit, onAddFriend: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(member.name, member.id, size = 64.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(member.name, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Text("@${member.handle}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                member.homeGym?.let { gym ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Outlined.Place, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(gym, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        if (member.bio.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(member.bio, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onMessage, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Message")
            }
            FilledTonalButton(
                onClick = onAddFriend,
                enabled = !member.isFriend,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (member.isFriend) Icons.Filled.Check else Icons.Outlined.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (member.isFriend) "Friends" else "Add friend")
            }
        }
    }
}

@Composable
private fun StatsCard(state: FriendProfileUiState, member: Member) {
    val colors = MaterialTheme.colorScheme
    val theirName = firstName(member.name)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "You vs $theirName",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            headToHeadLine(state.headToHead, theirName)?.let { line ->
                val color = when {
                    state.headToHead.youLead > state.headToHead.theyLead -> colors.primary
                    state.headToHead.theyLead > state.headToHead.youLead -> colors.tertiary
                    else -> colors.onSurfaceVariant
                }
                DotChip(line, color)
            }
        }
        if (!state.youHaveWorkouts) {
            Text(
                "Finish a workout and your side fills in.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        state.stats.forEach { row ->
            Spacer(modifier = Modifier.height(16.dp))
            CompareBars(
                label = row.label,
                theirName = theirName,
                mine = row.mine,
                theirs = row.theirs,
                mineText = statValue(row.kind, row.mine, state.unit),
                theirsText = statValue(row.kind, row.theirs, state.unit),
                leader = row.leader
            )
        }
    }
}

@Composable
private fun LiftsCard(state: FriendProfileUiState, member: Member) {
    val colors = MaterialTheme.colorScheme
    val theirName = firstName(member.name)
    SectionCard {
        Text("Best lifts", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Text(
            "Heaviest weight logged on each lift",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        if (state.lifts.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("No lifts logged yet.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        state.lifts.forEach { lift ->
            Spacer(modifier = Modifier.height(16.dp))
            CompareBars(
                label = lift.name,
                theirName = theirName,
                mine = (lift.mineLbs ?: 0f).toDouble(),
                theirs = (lift.theirsLbs ?: 0f).toDouble(),
                mineText = liftValue(lift.mineLbs, state.unit),
                theirsText = liftValue(lift.theirsLbs, state.unit),
                leader = lift.leader
            )
        }
    }
}

package com.redline.app.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.redline.app.community.Conversation
import com.redline.app.community.FeedPost
import com.redline.app.community.Gym
import com.redline.app.community.Member
import com.redline.app.ui.components.EmptyState
import com.redline.app.ui.components.PageHeader
import com.redline.app.ui.components.ScreenPadding
import com.redline.app.ui.components.SectionCard
import com.redline.app.ui.components.SectionLabel
import com.redline.app.ui.components.WellShape
import com.redline.app.ui.components.plural
import com.redline.app.ui.navigation.Routes
import com.redline.app.ui.theme.RedlineTheme
import com.redline.app.ui.theme.Tabular
import java.time.ZoneId

enum class CommunityTab(val label: String) {
    FEED("Feed"), GYMS("Gyms"), FRIENDS("Friends"), MESSAGES("Messages")
}

private enum class GymSort(val label: String) { NEAREST("Nearest"), QUIETEST("Quietest") }

@Composable
fun CommunityScreen(navController: NavController, viewModel: CommunityViewModel = hiltViewModel()) {
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val gyms by viewModel.gyms.collectAsStateWithLifecycle()
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val unread by viewModel.unreadCount.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val unit by viewModel.unit.collectAsStateWithLifecycle()
    val lastWorkout by viewModel.lastWorkout.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(CommunityTab.FEED) }
    val now = rememberNow()

    // One scroll position per tab, kept while you switch between them.
    val feedList = rememberLazyListState()
    val gymList = rememberLazyListState()
    val friendList = rememberLazyListState()
    val messageList = rememberLazyListState()

    val openProfile: (String) -> Unit = { id -> navController.navigate(Routes.communityFriend(id)) }
    val openChat: (String) -> Unit = { id -> navController.navigate(Routes.communityChat(id)) }
    val startChat: (String) -> Unit = { memberId -> viewModel.openConversation(memberId, openChat) }

    // Room under the last card for the floating nav pill.
    val listPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 112.dp)

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = ScreenPadding)) {
            PageHeader(
                title = "Community",
                subtitle = "Sample people for now. What you post and send stays on this phone."
            )
            Spacer(modifier = Modifier.height(12.dp))
            CommunityTabs(selected = tab, unread = unread, onSelect = { tab = it })
        }

        when (tab) {
            CommunityTab.FEED -> FeedTab(
                posts = feed,
                userName = userName,
                lastWorkout = lastWorkout,
                now = now,
                state = feedList,
                contentPadding = listPadding,
                onPost = viewModel::post,
                onLike = viewModel::toggleLike,
                onOpenProfile = openProfile
            )
            CommunityTab.GYMS -> GymsTab(gyms, unit, gymList, listPadding)
            CommunityTab.FRIENDS -> FriendsTab(
                friends = friends,
                suggestions = suggestions,
                state = friendList,
                contentPadding = listPadding,
                onOpenProfile = openProfile,
                onAddFriend = viewModel::addFriend
            )
            CommunityTab.MESSAGES -> MessagesTab(
                conversations = conversations,
                friends = friends,
                now = now,
                state = messageList,
                contentPadding = listPadding,
                onOpenChat = openChat,
                onStartChat = startChat
            )
        }
    }
}

/** Four segments in one rounded track, the selected one filled like the nav pill. */
@Composable
private fun CommunityTabs(selected: CommunityTab, unread: Int, onSelect: (CommunityTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainer)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        CommunityTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val content = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isSelected) colors.primaryContainer else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (tab == CommunityTab.MESSAGES && unread > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(colors.primary, CircleShape)
                            .semantics { contentDescription = "$unread unread" }
                    )
                }
            }
        }
    }
}

/* Feed */

@Composable
private fun FeedTab(
    posts: List<FeedPost>?,
    userName: String,
    lastWorkout: String?,
    now: Long,
    state: LazyListState,
    contentPadding: PaddingValues,
    onPost: (String, Boolean) -> Unit,
    onLike: (String) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "composer") { Composer(userName, lastWorkout, onPost) }
        if (posts == null) return@LazyColumn
        if (posts.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.Forum,
                    title = "Nothing posted yet",
                    body = "Share a lift or a PR above and it shows up here."
                )
            }
        }
        items(posts, key = { "post-${it.id}" }) { post ->
            PostCard(
                post = post,
                age = postAge(post.createdAt, now, zone),
                onLike = { onLike(post.id) },
                onOpenProfile = onOpenProfile
            )
        }
    }
}

@Composable
private fun Composer(userName: String, lastWorkout: String?, onPost: (String, Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }
    var attach by rememberSaveable { mutableStateOf(false) }

    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            Avatar(name = userName.ifBlank { "You" }, seed = "me", isMe = true, modifier = Modifier.padding(top = 8.dp))
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Share a lift, a PR or a question") },
                minLines = 2,
                maxLines = 6,
                shape = WellShape,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        }
        if (attach && lastWorkout != null) {
            Spacer(modifier = Modifier.height(12.dp))
            WorkoutStrip(lastWorkout)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (lastWorkout != null) Arrangement.SpaceBetween else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (lastWorkout != null) {
                ToggleChip(
                    label = "Attach last workout",
                    checked = attach,
                    onCheckedChange = { attach = it },
                    icon = Icons.Outlined.FitnessCenter,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp)
                )
            }
            Button(
                onClick = {
                    onPost(text, attach && lastWorkout != null)
                    text = ""
                    attach = false
                    focus.clearFocus()
                },
                enabled = text.isNotBlank()
            ) {
                Text("Post")
            }
        }
        if (lastWorkout == null) {
            Text(
                "Finish a workout to attach it to a post.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** A workout attached to a post: "Leg day · 16 sets · 14.2k lbs". */
@Composable
private fun WorkoutStrip(summary: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerHigh, WellShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.FitnessCenter, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(summary, style = MaterialTheme.typography.labelLarge.merge(Tabular), color = colors.onSurface)
    }
}

@Composable
private fun PostCard(post: FeedPost, age: String, onLike: () -> Unit, onOpenProfile: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val author = post.author
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val authorModifier = if (author.isMe) Modifier else Modifier
                .clip(WellShape)
                .clickable(onClickLabel = "Open profile") { onOpenProfile(author.id) }
            Row(modifier = Modifier.weight(1f).then(authorModifier), verticalAlignment = Alignment.CenterVertically) {
                Avatar(author.name, author.id, isMe = author.isMe)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(author.name, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    val detail = if (author.isMe) "Only on this phone"
                    else listOfNotNull("@${author.handle}", post.gymName).joinToString(" · ")
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(age, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(post.text, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
        post.workoutSummary?.let {
            Spacer(modifier = Modifier.height(12.dp))
            WorkoutStrip(it)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val likeColor = if (post.likedByMe) colors.primary else colors.onSurfaceVariant
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .toggleable(value = post.likedByMe, role = Role.Checkbox, onValueChange = { onLike() })
                    .semantics(mergeDescendants = true) { contentDescription = "Like, ${plural(post.likeCount, "like")}" }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (post.likedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = likeColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(post.likeCount.toString(), style = MaterialTheme.typography.labelLarge.merge(Tabular), color = likeColor)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .semantics(mergeDescendants = true) { contentDescription = plural(post.commentCount, "comment") }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(post.commentCount.toString(), style = MaterialTheme.typography.labelLarge.merge(Tabular), color = colors.onSurfaceVariant)
            }
        }
    }
}

/* Gyms */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GymsTab(gyms: List<Gym>?, unit: String, state: LazyListState, contentPadding: PaddingValues) {
    var sort by rememberSaveable { mutableStateOf(GymSort.NEAREST) }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "sort") {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
                    GymSort.entries.forEach { option ->
                        ChoiceChip(option.label, selected = sort == option, onClick = { sort = option })
                    }
                }
                Text(
                    "Sample gyms. Distances and crowd levels aren't live yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        if (gyms == null) return@LazyColumn
        val sorted = when (sort) {
            GymSort.NEAREST -> gyms.sortedBy { it.distanceMiles }
            GymSort.QUIETEST -> gyms.sortedWith(compareBy<Gym> { it.crowdPercent }.thenBy { it.distanceMiles })
        }
        items(sorted, key = { "gym-${it.id}" }) { gym -> GymCard(gym, unit) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GymCard(gym: Gym, unit: String) {
    val colors = MaterialTheme.colorScheme
    val level = crowdLevel(gym.crowdPercent)
    val crowdColor = when (level) {
        CrowdLevel.QUIET -> RedlineTheme.status.success
        CrowdLevel.MODERATE -> RedlineTheme.status.warning
        CrowdLevel.BUSY -> colors.primary
    }
    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(gym.name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(
                    "${gym.area} · ${gym.hours}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    distanceLabel(gym.distanceMiles, unit),
                    style = MaterialTheme.typography.labelLarge.merge(Tabular),
                    color = colors.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "${level.label}, ${gym.crowdPercent}% full"
            }
        ) {
            DotChip(level.label, crowdColor)
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.surfaceContainerHigh)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((gym.crowdPercent / 100f).coerceIn(0.02f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(crowdColor)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "${gym.crowdPercent}% full",
                style = MaterialTheme.typography.labelMedium.merge(Tabular),
                color = colors.onSurfaceVariant
            )
        }
        if (gym.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                gym.tags.forEach { TagChip(it) }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = colors.outlineVariant)
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Groups, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "${plural(gym.memberCount, "member")} here",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }
        friendsHereLine(gym.friendsHere)?.let { line ->
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Group, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            }
        }
    }
}

/* Friends */

@Composable
private fun FriendsTab(
    friends: List<Member>?,
    suggestions: List<Member>?,
    state: LazyListState,
    contentPadding: PaddingValues,
    onOpenProfile: (String) -> Unit,
    onAddFriend: (String) -> Unit
) {
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (friends == null || suggestions == null) return@LazyColumn

        item(key = "friends-label") { SectionLabel("Friends · ${friends.size}") }
        if (friends.isEmpty()) {
            item(key = "friends-empty") {
                EmptyState(
                    icon = Icons.Outlined.Group,
                    title = "No friends yet",
                    body = "Add people below to compare workouts and message them."
                )
            }
        } else {
            item(key = "friends") {
                SectionCard(contentPadding = 0.dp) {
                    friends.forEachIndexed { i, member ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 68.dp))
                        FriendRow(member, onClick = { onOpenProfile(member.id) })
                    }
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            item(key = "suggestions-label") {
                SectionLabel("People you may know", modifier = Modifier.padding(top = 8.dp))
            }
            item(key = "suggestions") {
                SectionCard(contentPadding = 0.dp) {
                    suggestions.forEachIndexed { i, member ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 68.dp))
                        SuggestionRow(
                            member,
                            onClick = { onOpenProfile(member.id) },
                            onAdd = { onAddFriend(member.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRow(member: Member, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = "Compare", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(member.name, member.id)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(member.name, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Text(
                listOfNotNull("@${member.handle}", member.homeGym).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                member.stats.workoutsThisWeek.toString(),
                style = MaterialTheme.typography.titleMedium.merge(Tabular),
                color = colors.onSurface
            )
            Text("this week", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

@Composable
private fun SuggestionRow(member: Member, onClick: () -> Unit, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = "Open profile", onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(member.name, member.id)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(member.name, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Text(
                member.bio,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        FilledTonalButton(
            onClick = onAdd,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.semantics { contentDescription = "Add ${member.name}" }
        ) {
            Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add")
        }
    }
}

/* Messages */

@Composable
private fun MessagesTab(
    conversations: List<Conversation>?,
    friends: List<Member>?,
    now: Long,
    state: LazyListState,
    contentPadding: PaddingValues,
    onOpenChat: (String) -> Unit,
    onStartChat: (String) -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (conversations == null || friends == null) return@LazyColumn

        item(key = "note") {
            Text(
                "Messages are saved on this phone. Nobody else gets them until Redline has accounts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (friends.isNotEmpty()) {
            item(key = "start") {
                Column {
                    SectionLabel("Start a chat")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(friends, key = { it.id }) { friend ->
                            Column(
                                modifier = Modifier
                                    .clip(WellShape)
                                    .clickable(onClickLabel = "Message ${friend.name}") { onStartChat(friend.id) }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                                    .width(60.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Avatar(friend.name, friend.id, size = 52.dp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    firstName(friend.name),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        if (conversations.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.Forum,
                    title = "No messages yet",
                    body = "Pick a friend above, or tap Message on anyone's profile."
                )
            }
        } else {
            item(key = "threads") {
                SectionCard(contentPadding = 0.dp) {
                    conversations.forEachIndexed { i, conversation ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 76.dp))
                        ConversationRow(
                            conversation = conversation,
                            time = conversation.lastMessage?.let { threadTime(it.sentAt, now, zone) }.orEmpty(),
                            onClick = { onOpenChat(conversation.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, time: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val unread = conversation.unreadCount > 0
    val last = conversation.lastMessage
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(conversation.member.name, conversation.member.id, size = 48.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.member.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread) FontWeight.Bold else null,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    time,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unread) colors.primary else colors.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        last == null -> "No messages yet"
                        last.fromMe -> "You: ${last.text}"
                        else -> last.text
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unread) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (unread) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CountBadge(
                        conversation.unreadCount,
                        modifier = Modifier.semantics { contentDescription = "${conversation.unreadCount} unread" }
                    )
                }
            }
        }
    }
}

package com.redline.app.ui.community

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.community.CommunityRepository
import com.redline.app.community.HeadToHead
import com.redline.app.community.LiftComparison
import com.redline.app.community.Member
import com.redline.app.community.StatComparison
import com.redline.app.community.athleteStatsFrom
import com.redline.app.community.compareLifts
import com.redline.app.community.compareStats
import com.redline.app.community.headToHead
import com.redline.app.data.repository.WorkoutRepository
import com.redline.app.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class FriendProfileUiState(
    val isLoading: Boolean = true,
    /** Null once loaded means there's nobody with that id. */
    val member: Member? = null,
    val stats: List<StatComparison> = emptyList(),
    val lifts: List<LiftComparison> = emptyList(),
    val headToHead: HeadToHead = HeadToHead(0, 0, 0),
    val unit: String = "lbs",
    /** False until you finish a workout, so the comparison can say why your side is empty. */
    val youHaveWorkouts: Boolean = false
)

@HiltViewModel
class FriendProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val community: CommunityRepository,
    workouts: WorkoutRepository,
    settings: SettingsStore
) : ViewModel() {

    private val memberId: String = checkNotNull(savedStateHandle["memberId"])

    val uiState: StateFlow<FriendProfileUiState> = combine(
        community.member(memberId),
        workouts.getAllSessions(),
        workouts.getAllSets(),
        settings.defaultUnit
    ) { member, sessions, sets, unit ->
        val zone = ZoneId.systemDefault()
        val mine = athleteStatsFrom(sessions, sets, LocalDate.now(zone), zone)
        val stats = member?.let { compareStats(mine, it.stats) }.orEmpty()
        val lifts = member?.let { compareLifts(mine.bestLifts, it.stats.bestLifts) }.orEmpty()
        FriendProfileUiState(
            isLoading = false,
            member = member,
            stats = stats,
            lifts = lifts,
            headToHead = headToHead(stats, lifts),
            unit = unit,
            youHaveWorkouts = sessions.any { it.endTime != null }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FriendProfileUiState())

    fun addFriend() {
        viewModelScope.launch { community.addFriend(memberId) }
    }

    fun openChat(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(community.openConversation(memberId)) }
    }
}

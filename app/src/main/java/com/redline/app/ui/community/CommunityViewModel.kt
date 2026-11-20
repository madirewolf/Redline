package com.redline.app.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.community.CommunityRepository
import com.redline.app.community.Conversation
import com.redline.app.community.FeedPost
import com.redline.app.community.Gym
import com.redline.app.community.Member
import com.redline.app.data.repository.WorkoutRepository
import com.redline.app.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val community: CommunityRepository,
    workouts: WorkoutRepository,
    settings: SettingsStore
) : ViewModel() {

    // Null until the first answer, so the tabs don't flash an empty state.
    val feed: StateFlow<List<FeedPost>?> = community.feed().stateInScope()
    val gyms: StateFlow<List<Gym>?> = community.gyms().stateInScope()
    val friends: StateFlow<List<Member>?> = community.friends().stateInScope()
    val suggestions: StateFlow<List<Member>?> = community.suggestions().stateInScope()
    val conversations: StateFlow<List<Conversation>?> = community.conversations().stateInScope()

    val unreadCount: StateFlow<Int> = community.conversations()
        .map { threads -> threads.sumOf { it.unreadCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val userName: StateFlow<String> = settings.userName
    val unit: StateFlow<String> = settings.defaultUnit

    /** Your latest finished workout as one line, ready to attach to a post. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val lastWorkout: StateFlow<String?> = workouts.getRecentCompletedSessions(1)
        .flatMapLatest { sessions ->
            val session = sessions.firstOrNull() ?: return@flatMapLatest flowOf(null)
            workouts.getSetsForSession(session.id).map { sets -> workoutLine(session, sets, ZoneId.systemDefault()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun post(text: String, attachWorkout: Boolean) {
        val summary = if (attachWorkout) lastWorkout.value else null
        viewModelScope.launch { community.createPost(text, summary) }
    }

    fun toggleLike(postId: String) {
        viewModelScope.launch { community.toggleLike(postId) }
    }

    fun addFriend(memberId: String) {
        viewModelScope.launch { community.addFriend(memberId) }
    }

    /** Finds or starts your thread with [memberId], then hands back its id. */
    fun openConversation(memberId: String, onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(community.openConversation(memberId)) }
    }

    private fun <T> Flow<T>.stateInScope(): StateFlow<T?> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

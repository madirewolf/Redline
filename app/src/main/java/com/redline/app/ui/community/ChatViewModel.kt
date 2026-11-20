package com.redline.app.ui.community

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redline.app.community.CommunityRepository
import com.redline.app.community.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class ChatUiState(
    val isLoading: Boolean = true,
    /** Null once loaded means the thread doesn't exist. */
    val conversation: Conversation? = null,
    val rows: List<ChatRow> = emptyList()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val community: CommunityRepository
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    val uiState: StateFlow<ChatUiState> = combine(
        community.conversation(conversationId),
        // While the thread is on screen, whatever arrives counts as read.
        community.messages(conversationId)
            .onEach { messages -> if (messages.any { !it.fromMe }) community.markRead(conversationId) }
    ) { conversation, messages ->
        ChatUiState(
            isLoading = false,
            conversation = conversation,
            rows = chatRows(messages, ZoneId.systemDefault())
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    fun send(text: String) {
        viewModelScope.launch { community.sendMessage(conversationId, text) }
    }
}

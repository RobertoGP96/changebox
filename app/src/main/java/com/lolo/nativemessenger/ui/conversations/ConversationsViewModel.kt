package com.lolo.nativemessenger.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolo.nativemessenger.data.ChatRepository
import com.lolo.nativemessenger.data.model.Conversation
import com.lolo.nativemessenger.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ConversationsViewModel(
    repository: ChatRepository = ServiceLocator.chatRepository,
) : ViewModel() {

    val conversations: StateFlow<List<Conversation>> =
        repository.observeConversations()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )
}

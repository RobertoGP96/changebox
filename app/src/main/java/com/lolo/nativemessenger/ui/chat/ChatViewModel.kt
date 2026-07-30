package com.lolo.nativemessenger.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lolo.nativemessenger.data.ChatRepository
import com.lolo.nativemessenger.data.model.Message
import com.lolo.nativemessenger.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    conversationId: String,
    private val repository: ChatRepository,
) : ViewModel() {

    val messages: StateFlow<List<Message>> =
        repository.observeMessages(conversationId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    var draft by mutableStateOf("")
        private set

    private val currentConversationId = conversationId

    fun onDraftChange(value: String) {
        draft = value
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        draft = ""
        viewModelScope.launch {
            repository.sendMessage(currentConversationId, text)
        }
    }

    companion object {
        fun factory(conversationId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatViewModel(conversationId, ServiceLocator.chatRepository)
            }
        }
    }
}

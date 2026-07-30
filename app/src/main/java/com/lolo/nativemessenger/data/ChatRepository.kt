package com.lolo.nativemessenger.data

import com.lolo.nativemessenger.data.model.Conversation
import com.lolo.nativemessenger.data.model.Message
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Contrato de datos. Cuando integres un backend real (Supabase, Firebase, tu API),
 * crea otra implementación de esta interfaz y los ViewModels no cambian.
 */
interface ChatRepository {
    fun observeConversations(): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<Message>>
    suspend fun sendMessage(conversationId: String, text: String)
}

/**
 * Implementación en memoria con datos de ejemplo y una auto-respuesta simulada,
 * para que la app sea funcional desde el primer arranque.
 */
class InMemoryChatRepository : ChatRepository {

    private val now = System.currentTimeMillis()

    private val messages = MutableStateFlow(
        listOf(
            Message("m1", "c1", "¡Hola! ¿Cómo va el proyecto?", isMine = false, timestamp = now - 3_600_000),
            Message("m2", "c1", "Avanzando, ya tengo el starter en Kotlin", isMine = true, timestamp = now - 3_500_000),
            Message("m3", "c1", "Compose es más fácil de lo que pensaba", isMine = true, timestamp = now - 3_400_000),
            Message("m4", "c2", "¿Viste la nueva versión de Kotlin?", isMine = false, timestamp = now - 86_400_000),
            Message("m5", "c3", "Nos vemos el sábado 👍", isMine = false, timestamp = now - 172_800_000),
        ),
    )

    private val contacts = mapOf(
        "c1" to "Ana García",
        "c2" to "Dev Team",
        "c3" to "Carlos",
    )

    override fun observeConversations(): Flow<List<Conversation>> =
        messages.map { list ->
            contacts.map { (id, name) ->
                val last = list.filter { it.conversationId == id }.maxByOrNull { it.timestamp }
                Conversation(
                    id = id,
                    contactName = name,
                    lastMessage = last?.text.orEmpty(),
                    timestamp = last?.timestamp ?: 0L,
                    unreadCount = list.count { it.conversationId == id && !it.isMine },
                )
            }.sortedByDescending { it.timestamp }
        }

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        messages.map { list ->
            list.filter { it.conversationId == conversationId }.sortedBy { it.timestamp }
        }

    override suspend fun sendMessage(conversationId: String, text: String) {
        appendMessage(conversationId, text, isMine = true)

        // Auto-respuesta simulada: elimina esto cuando conectes un backend real
        delay(1_200)
        appendMessage(conversationId, "Recibido: \"$text\" ✔", isMine = false)
    }

    private fun appendMessage(conversationId: String, text: String, isMine: Boolean) {
        messages.value = messages.value + Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            text = text,
            isMine = isMine,
            timestamp = System.currentTimeMillis(),
        )
    }
}

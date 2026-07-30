package com.lolo.nativemessenger.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryChatRepositoryTest {

    @Test
    fun `las conversaciones iniciales existen y estan ordenadas por fecha`() = runTest {
        val repo = InMemoryChatRepository()

        val conversations = repo.observeConversations().first()

        assertEquals(3, conversations.size)
        assertTrue(
            conversations.zipWithNext().all { (a, b) -> a.timestamp >= b.timestamp },
        )
    }

    @Test
    fun `enviar un mensaje lo agrega a la conversacion`() = runTest {
        val repo = InMemoryChatRepository()
        val before = repo.observeMessages("c1").first().size

        repo.sendMessage("c1", "Mensaje de prueba")

        val after = repo.observeMessages("c1").first()
        // El mensaje propio + la auto-respuesta simulada
        assertEquals(before + 2, after.size)
        assertTrue(after.any { it.text == "Mensaje de prueba" && it.isMine })
    }
}

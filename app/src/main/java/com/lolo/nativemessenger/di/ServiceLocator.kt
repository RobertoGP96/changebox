package com.lolo.nativemessenger.di

import com.lolo.nativemessenger.data.ChatRepository
import com.lolo.nativemessenger.data.InMemoryChatRepository

/**
 * Inyección de dependencias manual, suficiente para empezar.
 * Cuando el proyecto crezca, migra a Hilt (com.google.dagger:hilt-android).
 */
object ServiceLocator {
    val chatRepository: ChatRepository by lazy { InMemoryChatRepository() }
}

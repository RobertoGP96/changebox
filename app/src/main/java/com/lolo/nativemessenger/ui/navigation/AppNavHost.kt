package com.lolo.nativemessenger.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lolo.nativemessenger.ui.chat.ChatScreen
import com.lolo.nativemessenger.ui.conversations.ConversationsScreen

object Routes {
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat/{conversationId}/{contactName}"

    fun chat(conversationId: String, contactName: String) =
        "chat/$conversationId/$contactName"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.CONVERSATIONS,
    ) {
        composable(Routes.CONVERSATIONS) {
            ConversationsScreen(
                onConversationClick = { conversation ->
                    navController.navigate(
                        Routes.chat(conversation.id, conversation.contactName),
                    )
                },
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("contactName") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId").orEmpty()
            val contactName = backStackEntry.arguments?.getString("contactName").orEmpty()
            ChatScreen(
                conversationId = conversationId,
                contactName = contactName,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

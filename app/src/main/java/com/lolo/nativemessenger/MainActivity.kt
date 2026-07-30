package com.lolo.nativemessenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lolo.nativemessenger.ui.navigation.AppNavHost
import com.lolo.nativemessenger.ui.theme.NativeMessengerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NativeMessengerTheme {
                AppNavHost()
            }
        }
    }
}

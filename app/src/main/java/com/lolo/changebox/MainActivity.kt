package com.lolo.changebox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.lolo.changebox.di.LocalAppContainer
import com.lolo.changebox.ui.ChangeboxApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ChangeboxApplication).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                ChangeboxApp()
            }
        }
    }
}



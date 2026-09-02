package com.skb8.vivotool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.skb8.vivotool.ui.MainScreen
import com.skb8.vivotool.ui.theme.VivoToolTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VivoToolTheme {
                MainScreen()
            }
        }
    }
}

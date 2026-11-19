package com.drbaysinger.audiobc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.drbaysinger.audiobc.ui.AudioBcScreen
import com.drbaysinger.audiobc.ui.AudioBcTheme
import com.drbaysinger.audiobc.ui.Bg

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudioBcTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    AudioBcScreen()
                }
            }
        }
    }
}

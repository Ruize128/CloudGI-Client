package io.github.ruize128.cloudgi.client

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App(platformName = "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
        }
    }
}

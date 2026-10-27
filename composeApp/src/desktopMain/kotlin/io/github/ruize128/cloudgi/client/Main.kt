package io.github.ruize128.cloudgi.client

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "CloudGI Client",
        state = rememberWindowState(width = 720.dp, height = 720.dp),
    ) {
        App(platformName = "${System.getProperty("os.name")} · ${System.getProperty("os.arch")}")
    }
}

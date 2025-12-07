package ru.mephi.k_not_mean

import ClusteringScreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {

    Window(
        onCloseRequest = ::exitApplication,
        title = "Cluster App",
    ) {
        MaterialTheme {
            ClusteringScreen()
        }
    }
}


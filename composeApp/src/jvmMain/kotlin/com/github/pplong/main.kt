package com.github.pplong

import KoinInitializer
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.koin.core.context.startKoin

fun main() = application {
    KoinInitializer().init()
    Window(
        onCloseRequest = ::exitApplication,
        title = "UniFTP",
    ) {
        App()
    }
}
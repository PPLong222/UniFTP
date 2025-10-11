package com.github.pplong

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
package com.github.pplong.core.utils

fun String.appendFilePath(name: String): String {
    return if (this.endsWith("/")) {
        this.plus(name)
    } else {
        "$this/$name"
    }
}


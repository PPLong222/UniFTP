package com.github.pplong.core.utils

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun String.appendFilePath(name: String): String {
    return if (this.endsWith("/")) {
        this.plus(name)
    } else {
        "$this/$name"
    }
}

object DateUtil {
    @OptIn(ExperimentalTime::class)
    fun getFormatDate(timeMills: Long): String {
        val instant = Instant.fromEpochMilliseconds(timeMills)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${localDateTime.year}-${localDateTime.month.number}-${localDateTime.day} ${localDateTime.hour}:${localDateTime.minute}"
    }
}

object FileUtil {
    private val SIZE_ARR = arrayOf("B", "KB", "MB", "GB")
    fun getFileSize(size: Long): String {
        var formatSize = size.toDouble()
        var i = 0
        while (formatSize / 1024.0 > 1 && i < SIZE_ARR.size - 1) {
            formatSize /= 1024.0
            i++
        }

        val rounded = roundToTwoDecimals(formatSize)
        return "$rounded${SIZE_ARR[i]}"
    }

    private fun roundToTwoDecimals(value: Double): String {
        val scaled = (value * 100).toInt()
        val result = scaled / 100.0
        return result.toString()
    }
}

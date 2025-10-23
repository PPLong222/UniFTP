package com.github.pplong.core.def

import org.jetbrains.compose.resources.DrawableResource
import uniftp.composeapp.generated.resources.Res
import uniftp.composeapp.generated.resources.ic_audio_file
import uniftp.composeapp.generated.resources.ic_draft
import uniftp.composeapp.generated.resources.ic_format_left
import uniftp.composeapp.generated.resources.ic_image
import uniftp.composeapp.generated.resources.ic_terminal
import uniftp.composeapp.generated.resources.ic_video_file

enum class FileType(
    val category: String,
    val mimePrefix: String,
    val extensions: Set<String>
) {
    IMAGE("image", "image/", setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")),
    VIDEO("video", "video/", setOf("mp4", "mkv", "avi", "mov")),
    AUDIO("audio", "audio/", setOf("mp3", "wav", "ogg", "flac")),
    PDF("pdf", "application/pdf", setOf("pdf")),
    TEXT("text", "text/", setOf("txt", "md", "log", "csv", "html")),
    WORD("word", "application/msword", setOf("doc", "docx")),
    EXCEL("excel", "application/vnd.ms-excel", setOf("xls", "xlsx")),
    POWERPOINT("powerpoint", "application/vnd.ms-powerpoint", setOf("ppt", "pptx")),
    ARCHIVE("archive", "application/zip", setOf("zip", "rar", "7z", "tar", "gz")),
    BINARY("binary", "application/octet-stream", setOf("apk", "exe", "dmg")),
    OTHER("other", "*/*", emptySet());

    companion object {
        fun fromFileName(fileName: String?): FileType {
            if (fileName.isNullOrEmpty()) return OTHER
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { it.extensions.contains(ext) } ?: OTHER
        }

        fun fromMimeType(mime: String?): FileType {
            if (mime.isNullOrEmpty()) return OTHER
            val lower = mime.lowercase()
            return entries.firstOrNull {
                lower == it.mimePrefix || lower.startsWith(it.mimePrefix)
            } ?: OTHER
        }
    }
}

fun FileType.toDrawableRes(): DrawableResource {
    return when (this) {
        FileType.IMAGE -> Res.drawable.ic_image
        FileType.VIDEO -> Res.drawable.ic_video_file
        FileType.AUDIO -> Res.drawable.ic_audio_file
        FileType.TEXT -> Res.drawable.ic_format_left
        FileType.BINARY -> Res.drawable.ic_terminal
        FileType.PDF, FileType.WORD, FileType.POWERPOINT, FileType.EXCEL, FileType.OTHER, FileType.ARCHIVE -> Res.drawable.ic_draft
    }
}
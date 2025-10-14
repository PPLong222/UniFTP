package com.github.pplong.feat.browse

data class FTPFileUiModel(
    val name: String,
    val path: String,
    val parentPath: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val modifiedTime: Long = 0L,
    val permissions: String? = null,
    val owner: String? = null,
    val group: String? = null
)
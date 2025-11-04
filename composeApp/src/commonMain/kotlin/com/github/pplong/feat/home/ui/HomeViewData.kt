package com.github.pplong.feat.home.ui

data class EditFTPServerItem (
    val host: String = "",
    val password: String = "",
    val user: String = "",
    val port: Int = 22,
    val nickname: String = "",
    val lastConnectedTime: Long = 0,
    val downloadDir: String? = null,
    val useDefaultDownloadDir: Boolean = true,
    val defaultDownloadDir: String = ""
)

enum class EditConfigureState {
    CONNECTING,
    CONFIGURING
}
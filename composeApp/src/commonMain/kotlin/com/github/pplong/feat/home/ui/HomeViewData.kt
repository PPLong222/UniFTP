package com.github.pplong.feat.home.ui

data class EditFTPServerItem (
    val host: String = "185.211.4.19",
    val password: String = "88888888",
    val user: String = "sftpuser",
    val port: Int = 22,
    val nickname: String = "Test",
    val lastConnectedTime: Long = 0,
    val downloadDir: String? = null
)

enum class EditConfigureState {
    CONNECTING,
    CONFIGURING
}
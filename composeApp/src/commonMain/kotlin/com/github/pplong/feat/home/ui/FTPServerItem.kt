package com.github.pplong.feat.home.ui

import com.github.pplong.feat.home.model.FTPServer
import kotlinx.serialization.Serializable

@Serializable
data class FTPServerItem(
    val id: Long = 0,
    val host: String = "",
    val password: String = "",
    val user: String = "",
    val port: Int = 0,
    val nickname: String? = null,
    val lastConnectedTime: Long = 0,
)

fun FTPServer.toFTPServerItem(): FTPServerItem = FTPServerItem(
    id, host, password, user, port, nickname, lastConnectedTime
)

fun EditFTPServerItem.toFTPServer(): FTPServer = FTPServer(
    0, host, password, user, port, nickname, lastConnectedTime, downloadDir
)
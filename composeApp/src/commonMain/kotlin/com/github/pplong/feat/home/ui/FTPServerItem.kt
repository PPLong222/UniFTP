package com.github.pplong.feat.home.ui

import com.github.pplong.feat.home.model.FTPServer

data class FTPServerItem(
    val id: Long,
    val host: String,
    val password: String,
    val user: String,
    val port: Int,
    val nickname: String?,
    val lastConnectedTime: Long,
)

fun FTPServer.toFTPServerItem(): FTPServerItem = FTPServerItem(
    id, host, password, user, port, nickname, lastConnectedTime
)

fun EditFTPServerItem.toFTPServer(): FTPServer = FTPServer(
    0, host, password, user, port, nickname, lastConnectedTime, downloadDir
)
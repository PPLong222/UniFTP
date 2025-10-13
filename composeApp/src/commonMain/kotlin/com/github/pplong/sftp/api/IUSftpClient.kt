package com.github.pplong.sftp.api

interface IUSftpClient {
    suspend fun connect(host: String, port: Int)

    suspend fun auth(user: String, password: String)
}
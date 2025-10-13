package com.github.pplong

import com.github.pplong.sftp.api.IUSftpClient
import kotlinx.cinterop.ExperimentalForeignApi
import libssh2.*

class Libssh2SftpClient: IUSftpClient {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun connect(host: String, port: Int) {
        println("link~ $LIBSSH2_VERSION")
    }

    override suspend fun auth(user: String, password: String) {
        
    }
}
package com.github.pplong.sftp

import com.github.pplong.sftp.def.FTPConfig
import com.github.pplong.sftp.def.FTPFile

class FTPClientManager(
    private val config: FTPConfig
) {
    lateinit var coreFTPClient: ICoreFTPClient

    suspend fun connect(): Boolean {
        coreFTPClient = SFTPClientFactory.create()
        return coreFTPClient.initClient(config)
    }

    suspend fun pwd(): String = coreFTPClient.pwd()

    suspend fun list(path: String): List<FTPFile> {
        return coreFTPClient.list(path)
    }

    suspend fun close() {
        coreFTPClient.close()
    }
}
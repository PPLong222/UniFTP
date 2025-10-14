package com.github.pplong.sftp

interface IBaseFTPClient {
    suspend fun initClient(config: FTPConfig): Boolean
    suspend fun close()
}
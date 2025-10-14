package com.github.pplong.sftp

import com.github.pplong.sftp.def.FTPConfig

interface IBaseFTPClient {
    suspend fun initClient(config: FTPConfig): Boolean
    suspend fun close()
}
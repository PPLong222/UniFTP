package com.github.pplong.sftp

import com.github.pplong.sftp.def.FTPFile

interface ICoreFTPClient : IBaseFTPClient {
    suspend fun list(path: String): List<FTPFile>

    suspend fun pwd(): String
}
package com.github.pplong.sftp

import com.github.pplong.feat.browse.FTPFileUiModel
import com.github.pplong.sftp.def.FTPFile

interface ICoreFTPClient : IBaseFTPClient {
    suspend fun list(path: String): List<FTPFile>

    suspend fun pwd(): String

    suspend fun delete(deleteFiles: List<FTPFileUiModel>, onProgress: (removedCount: Int) -> Unit)
    suspend fun mkdir(path: String)
}
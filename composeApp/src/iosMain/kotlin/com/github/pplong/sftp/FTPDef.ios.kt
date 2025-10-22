package com.github.pplong.sftp

actual object SFTPClientFactory {
    actual fun create(): ICoreFTPClient {
        return Libssh2CoreSftpClient()
    }

    actual fun createTransferClient(): ITransferFTPClient {
        return Libssh2TransferSftpClient()
    }
}
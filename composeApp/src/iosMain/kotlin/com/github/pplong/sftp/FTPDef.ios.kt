package com.github.pplong.sftp

actual object SFTPClientFactory {
    actual fun create(): ICoreFTPClient {
        return Libssh2CoreSftpClient()
    }
}
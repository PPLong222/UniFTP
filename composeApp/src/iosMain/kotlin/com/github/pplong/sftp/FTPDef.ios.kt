package com.github.pplong.sftp

actual object SFTPClientFactory {
    actual fun create(): IBaseFTPClient {
        return Libssh2SftpBaseClient()
    }
}
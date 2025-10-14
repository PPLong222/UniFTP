package com.github.pplong.sftp

expect object SFTPClientFactory {
    fun create(): ICoreFTPClient
}
package com.github.pplong.sftp

import com.github.pplong.sftp.def.FTPConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier

open class SshjSftpBaseClient : IBaseFTPClient {
    protected lateinit var ssh: SSHClient
    protected lateinit var config: FTPConfig
    override suspend fun initClient(config: FTPConfig): Boolean {
        return try {
            this.config = config
            ssh = SSHClient()
            ssh.addHostKeyVerifier(PromiscuousVerifier())
            ssh.connect(config.host, config.port)
            ssh.connection.keepAlive.keepAliveInterval = 30
            ssh.authPassword(config.username, config.password)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun close() {
        if (::ssh.isInitialized) {
            ssh.close()
        }
    }
}
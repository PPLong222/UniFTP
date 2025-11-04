package com.github.pplong.sftp

import com.github.pplong.sftp.def.FTPConfig
import com.github.pplong.sftp.def.FTPPasswordPass
import com.github.pplong.sftp.def.FTPPublicKeyPass
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
            when (config.pass) {
                is FTPPasswordPass -> authWithPassword(config.pass)
                is FTPPublicKeyPass -> authWithPublicKey(config.pass)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun authWithPassword(pass: FTPPasswordPass) {
        ssh.authPassword(config.user, pass.password)
    }

    private fun authWithPublicKey(pass: FTPPublicKeyPass) {
        val publicKey = if (pass.phrase == null) {
            ssh.loadKeys(pass.keyUri)
        } else {
            ssh.loadKeys(pass.keyUri, pass.phrase)
        }
        ssh.authPublickey(config.user, publicKey)
    }

    override suspend fun close() {
        if (::ssh.isInitialized) {
            ssh.close()
        }
    }
}
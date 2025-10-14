package com.github.pplong.sftp

import com.github.pplong.sftp.def.FTPConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.StatefulSFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier

open class SshjSftpBaseClient : IBaseFTPClient {
    protected lateinit var ssh: SSHClient
    protected lateinit var sftp: StatefulSFTPClient

    override suspend fun initClient(config: FTPConfig): Boolean {
        return try {
            ssh = SSHClient()
            ssh.addHostKeyVerifier(PromiscuousVerifier())
            ssh.connect(config.host, config.port)
            ssh.authPassword(config.username, config.password)
            sftp = ssh.newStatefulSFTPClient()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun close() {
        if (::sftp.isInitialized) {
            sftp.close()
        }
        if (::ssh.isInitialized) {
            ssh.close()
        }
    }

    /**
     * Get the current working directory path
     * @return The current working directory path, or null if not available
     */
    protected fun getCurrentPath(): String? {
        return try {
            if (::sftp.isInitialized) {
                sftp.canonicalize(".")
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
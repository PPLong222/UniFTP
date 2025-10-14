package com.github.pplong.sftp

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.StatefulSFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier

class SshjSftpBaseClient: IBaseFTPClient {
    lateinit var ssh: SSHClient
    lateinit var sftp: StatefulSFTPClient

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
        sftp.close()
        ssh.close()
    }
}
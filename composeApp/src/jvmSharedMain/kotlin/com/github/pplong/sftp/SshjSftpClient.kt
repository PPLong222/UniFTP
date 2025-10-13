package com.github.pplong.sftp

import com.github.pplong.sftp.api.IUSftpClient
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier

class SshjSftpClient: IUSftpClient {
    lateinit var sshClient : SSHClient
    override suspend fun connect(host: String, port: Int) {
        sshClient = SSHClient()
        sshClient.addHostKeyVerifier(PromiscuousVerifier())
        sshClient.connect(host, 22)
    }

    override suspend fun auth(user: String, password: String) {
        sshClient.authPassword(user, password)
    }
}
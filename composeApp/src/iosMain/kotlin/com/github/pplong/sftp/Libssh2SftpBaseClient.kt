package com.github.pplong.sftp

import kotlinx.cinterop.*
import libssh2.*
import platform.darwin.inet_pton
import platform.posix.*

class Libssh2SftpBaseClient: IBaseFTPClient {
    @OptIn(ExperimentalForeignApi::class)
    private var session: CPointer<LIBSSH2_SESSION>? = null
    @OptIn(ExperimentalForeignApi::class)
    private var sftp: CPointer<LIBSSH2_SFTP>? = null
    private var sock: Int = -1

    // Convert host byte order to network byte order (big-endian)
    private fun htons(port: UShort): UShort {
        return ((port.toUInt() shr 8) or (port.toUInt() shl 8)).toUShort()
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun initClient(config: FTPConfig): Boolean {
        return try {
            // Initialize libssh2
            if (libssh2_init(0) != 0) {
                println("Failed to initialize libssh2")
                return false
            }

            // Create socket
            sock = socket(AF_INET, SOCK_STREAM, 0)
            if (sock < 0) {
                println("Failed to create socket")
                libssh2_exit()
                return false
            }

            // Connect to server
            memScoped {
                val serverAddr = alloc<sockaddr_in>()
                serverAddr.sin_family = AF_INET.convert()
                serverAddr.sin_port = htons(config.port.toUShort()).convert()

                if (inet_pton(AF_INET, config.host, serverAddr.sin_addr.ptr) <= 0) {
                    println("Invalid address / Address not supported")
                    close(sock)
                    libssh2_exit()
                    return false
                }

                if (connect(sock, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
                    println("Connection failed")
                    close(sock)
                    libssh2_exit()
                    return false
                }
            }

            // Create session
            session = libssh2_session_init_ex(null, null, null, null)
            if (session == null) {
                println("Failed to create session")
                close(sock)
                libssh2_exit()
                return false
            }

            // Start SSH session
            if (libssh2_session_handshake(session, sock) != 0) {
                println("Failed to establish SSH session")
                cleanup()
                return false
            }

            // Authenticate with password
            val password = config.password ?: ""
            val usernameLen = config.username.length.toUInt()
            val passwordLen = password.length.toUInt()
            if (libssh2_userauth_password_ex(
                    session,
                    config.username,
                    usernameLen,
                    password,
                    passwordLen,
                    null
                ) != 0) {
                println("Authentication failed")
                cleanup()
                return false
            }

            // Initialize SFTP session
            sftp = libssh2_sftp_init(session)
            if (sftp == null) {
                println("Failed to initialize SFTP session")
                cleanup()
                return false
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
            false
        }
    }

    override suspend fun close() {
        cleanup()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun cleanup() {
        // Close SFTP session
        sftp?.let {
            libssh2_sftp_shutdown(it)
            sftp = null
        }

        // Disconnect session
        session?.let {
            libssh2_session_disconnect_ex(
                it,
                SSH_DISCONNECT_BY_APPLICATION,
                "Normal Shutdown",
                ""
            )
            libssh2_session_free(it)
            session = null
        }

        // Close socket
        if (sock >= 0) {
            close(sock)
            sock = -1
        }

        // Cleanup libssh2
        libssh2_exit()
    }
}
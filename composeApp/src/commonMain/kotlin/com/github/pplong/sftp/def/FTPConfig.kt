package com.github.pplong.sftp.def

data class FTPConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String? = null
)
package com.github.pplong.sftp.def

data class FTPConfig(
    val host: String,
    val port: Int,
    val user: String,
    val pass: FTPPass
)

sealed class FTPPass()

class FTPPasswordPass(
    val password: String
) : FTPPass()

class FTPPublicKeyPass(
    val keyUri: String,
    val phrase: String? = null
) : FTPPass()
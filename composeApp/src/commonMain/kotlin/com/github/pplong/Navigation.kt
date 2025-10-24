package com.github.pplong

import kotlinx.serialization.Serializable

@Serializable
object TestScreenNav

@Serializable
object SFTPScreenNav

@Serializable
object HomeScreenNav

@Serializable
class BrowseScreenNav(
    val id: Long,
    val nickname: String?,
    val host: String,
    val user: String,
    val port: Int,
    val password: String,
    val downloadDir: String?
)


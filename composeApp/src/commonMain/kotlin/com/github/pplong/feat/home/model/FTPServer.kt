package com.github.pplong.feat.home.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(
    tableName = "ftp_server",
    indices = [
        Index(value = ["host", "user"], unique = true),
        Index(value = ["nickname"], unique = true)
    ]
)
data class FTPServer (
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val host: String,
    val password: String,
    val user: String,
    val port: Int,
    val nickname: String,
    val lastConnectedTime: Long,
    val downloadDir: String?
)
package com.github.pplong.feat.home.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FTPServerDao {
    @Insert
    suspend fun insert(item: FTPServer)

    @Delete
    suspend fun delete(item: FTPServer)

    @Query("SELECT * from ftp_server")
    suspend fun getAll(): List<FTPServer>
}
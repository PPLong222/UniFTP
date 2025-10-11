package com.github.pplong.test

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow


@Entity
data class TestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String
)

@Dao
interface TestDao {
    @Insert
    suspend fun insert(item: TestEntity)

    @Query("SELECT * FROM TestEntity")
    fun getAllAsFlow(): Flow<List<TestEntity>>
}
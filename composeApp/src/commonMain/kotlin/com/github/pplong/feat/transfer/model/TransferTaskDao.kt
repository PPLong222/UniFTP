package com.github.pplong.feat.transfer.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for transfer tasks
 */
@Dao
interface TransferTaskDao {
    /**
     * Insert a new transfer task
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TransferTask)

    /**
     * Update an existing transfer task
     */
    @Update
    suspend fun update(task: TransferTask)

    /**
     * Delete a transfer task
     */
    @Query("DELETE FROM transfer_tasks WHERE id = :taskId")
    suspend fun delete(taskId: String)

    /**
     * Get a specific transfer task by ID
     */
    @Query("SELECT * FROM transfer_tasks WHERE id = :taskId")
    suspend fun getById(taskId: String): TransferTask?

    /**
     * Get all transfer tasks
     */
    @Query("SELECT * FROM transfer_tasks ORDER BY createdAt DESC")
    suspend fun getAll(): List<TransferTask>

    /**
     * Observe all transfer tasks (reactive)
     */
    @Query("SELECT * FROM transfer_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransferTask>>

    /**
     * Get all active transfer tasks (pending or in progress)
     */
    @Query("SELECT * FROM transfer_tasks WHERE status IN ('PENDING', 'IN_PROGRESS') ORDER BY createdAt ASC")
    suspend fun getActiveTasks(): List<TransferTask>

    /**
     * Get all paused transfer tasks (for iOS resume)
     */
    @Query("SELECT * FROM transfer_tasks WHERE status = 'PAUSED' ORDER BY createdAt ASC")
    suspend fun getPausedTasks(): List<TransferTask>

    /**
     * Get all failed transfer tasks
     */
    @Query("SELECT * FROM transfer_tasks WHERE status = 'FAILED' ORDER BY createdAt DESC")
    suspend fun getFailedTasks(): List<TransferTask>

    /**
     * Update task status
     */
    @Query("UPDATE transfer_tasks SET status = :status, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun updateStatus(taskId: String, status: TransferStatus, updatedAt: Long)

    /**
     * Update task progress
     */
    @Query("UPDATE transfer_tasks SET transferredBytes = :transferredBytes, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun updateProgress(taskId: String, transferredBytes: Long, updatedAt: Long)

    /**
     * Update total bytes (for uploads when file size is determined)
     */
    @Query("UPDATE transfer_tasks SET totalBytes = :totalBytes WHERE id = :taskId")
    suspend fun updateTotalBytes(taskId: String, totalBytes: Long)

    /**
     * Update task error
     */
    @Query("UPDATE transfer_tasks SET status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun updateError(taskId: String, status: TransferStatus, errorMessage: String, updatedAt: Long)

    /**
     * Delete all completed tasks
     */
    @Query("DELETE FROM transfer_tasks WHERE status = 'COMPLETED'")
    suspend fun deleteCompleted()

    /**
     * Delete all failed tasks
     */
    @Query("DELETE FROM transfer_tasks WHERE status = 'FAILED'")
    suspend fun deleteFailed()

    /**
     * Delete all tasks
     */
    @Query("DELETE FROM transfer_tasks")
    suspend fun deleteAll()
}

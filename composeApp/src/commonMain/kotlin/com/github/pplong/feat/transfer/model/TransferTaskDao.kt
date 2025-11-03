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
    suspend fun getTasksEmbeddedById(taskId: String): TransferTaskEmbedded?

    /**
     * Get all transfer tasks
     */
    @Query("SELECT * FROM transfer_tasks WHERE serverId = :serverId")
    fun getTasksEmbeddedByServerId(serverId: Long): Flow<List<TransferTaskEmbedded>>

    /**
     * Observe all transfer tasks (reactive)
     */
    @Query("SELECT * FROM transfer_tasks WHERE serverId = :serverId")
    fun observeAllTasksEmbeddedByServerId(serverId: Long): Flow<List<TransferTaskEmbedded>>

    /**
     * Get all active transfer tasks (pending or in progress)
     */
    @Query("SELECT * FROM transfer_tasks WHERE status IN ('PENDING', 'IN_PROGRESS')")
    suspend fun getActiveTasks(): List<TransferTask>

    /**
     * Get all paused transfer tasks (for iOS resume)
     */
    @Query("SELECT * FROM transfer_tasks WHERE status = 'PAUSED'")
    suspend fun getPausedTasks(): List<TransferTask>

    /**
     * Update task status
     */
    @Query("UPDATE transfer_tasks SET status = :status WHERE id = :taskId")
    suspend fun updateStatus(taskId: String, status: TransferStatus)

    /**
     * Update task status
     */
    @Query("UPDATE transfer_tasks SET status = :status, bytesTransferred = :bytesTransferred WHERE id = :taskId")
    suspend fun pausedStatus(taskId: String, status: TransferStatus, bytesTransferred: Long)

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

    /**
     * Update bytes transferred for a task
     */
    @Query("UPDATE transfer_tasks SET bytesTransferred = :bytesTransferred WHERE id = :taskId")
    suspend fun updateBytesTransferred(taskId: String, bytesTransferred: Long)

    @Query("UPDATE transfer_tasks SET localUri = :uri WHERE id = :taskId")
    suspend fun updateTransferUriByTaskId(taskId: String, uri: String)

    /**
     * Observe active tasks (pending or in progress)
     */
    @Query("SELECT * FROM transfer_tasks WHERE status IN ('PENDING', 'IN_PROGRESS')")
    fun observeActiveTasks(): Flow<List<TransferTaskEmbedded>>
}

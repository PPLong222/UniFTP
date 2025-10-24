package com.github.pplong.feat.transfer.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.github.pplong.feat.home.model.FTPServer

/**
 * Transfer direction enum
 */
enum class TransferDirection {
    UPLOAD,
    DOWNLOAD
}

class TransferDirectionConverters {
    @TypeConverter
    fun fromDirection(value: TransferDirection): String = value.name

    @TypeConverter
    fun toDirection(value: String): TransferDirection = enumValueOf(value)
}

/**
 * Transfer task status
 */
enum class TransferStatus {
    PENDING,      // Waiting to start
    IN_PROGRESS,  // Currently transferring
    PAUSED,       // Paused (for iOS background/foreground switches)
    COMPLETED,    // Transfer completed successfully
    FAILED,       // Transfer failed
    CANCELLED     // User cancelled the transfer
}

class TransferStatusConverters {
    @TypeConverter
    fun fromStatus(value: TransferStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): TransferStatus = enumValueOf(value)
}


/**
 * Transfer task entity for Room database
 * Stores transfer progress for resumable uploads/downloads
 */
@Entity(
    tableName = "transfer_tasks",
    foreignKeys = [
        ForeignKey(
            entity = FTPServer::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE,  // 删除服务器时级联删除任务
            onUpdate = ForeignKey.CASCADE   // 更新服务器 ID 时级联更新
        )
    ],
    indices = [Index(value = ["serverId"])]  // 为外键创建索引，提升查询性能
)
data class TransferTask(
    @PrimaryKey
    val id: String,  // Unique task ID (UUID)

    val direction: TransferDirection,
    val status: TransferStatus,

    // File information
    val fileName: String,
    val localUri: String,  // Local file URI (content:// on Android, file:// on iOS)
    val remotePath: String,  // Full remote path on SFTP server

    // Transfer progress
    val size: Long,
    @ColumnInfo
    val serverId: Long,

    val fileLastModified: Long,
) {
    /**
     * Check if transfer is complete
     */
    val isComplete: Boolean
        get() = status == TransferStatus.COMPLETED

    /**
     * Check if transfer is active (in progress or pending)
     */
    val isActive: Boolean
        get() = status == TransferStatus.PENDING || status == TransferStatus.IN_PROGRESS
}

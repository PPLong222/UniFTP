package com.github.pplong.feat.transfer.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

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
@Entity(tableName = "transfer_tasks")
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
    val transferredBytes: Long,
    val totalBytes: Long,

    // FTP server connection info (needed for resume)
    val serverHost: String,
    val serverPort: Int,
    val serverUsername: String,
    val serverPassword: String,  // TODO: Consider encrypting this

    // Optional: Download directory for downloads
    val downloadDir: String? = null,

    // Timestamps
    val createdAt: Long,
    val updatedAt: Long,

    // Error information
    val errorMessage: String? = null
) {
    /**
     * Calculate transfer progress (0.0 - 1.0)
     */
    val progress: Float
        get() = if (totalBytes > 0) {
            transferredBytes.toFloat() / totalBytes.toFloat()
        } else {
            0f
        }

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

    /**
     * Check if transfer can be resumed
     */
    val canResume: Boolean
        get() = (status == TransferStatus.PAUSED || status == TransferStatus.FAILED)
                && transferredBytes > 0
                && transferredBytes < totalBytes
}

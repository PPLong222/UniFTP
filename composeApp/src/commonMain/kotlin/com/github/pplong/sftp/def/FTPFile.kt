package com.github.pplong.sftp.def

/**
 * Represents a file or directory on the FTP/SFTP server
 *
 * @property name The name of the file or directory
 * @property path The full path of the file or directory
 * @property parentPath The parent directory path
 * @property isDirectory Whether this is a directory (true) or a file (false)
 * @property size File size in bytes (0 for directories)
 * @property modifiedTime Last modified timestamp in milliseconds since epoch
 * @property permissions File permissions (Unix-style, e.g., "rwxr-xr-x" or numeric like 755)
 * @property owner Owner of the file/directory (may be null if not available)
 * @property group Group of the file/directory (may be null if not available)
 */
data class FTPFile(
    val name: String,
    val path: String,
    val parentPath: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val modifiedTime: Long = 0L,
    val permissions: String? = null,
    val owner: String? = null,
    val group: String? = null
)

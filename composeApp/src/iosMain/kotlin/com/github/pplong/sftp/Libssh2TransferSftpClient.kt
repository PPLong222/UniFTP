package com.github.pplong.sftp

import kotlinx.cinterop.*
import libssh2.*
import platform.Foundation.*
import platform.posix.memcpy

/**
 * iOS implementation of ITransferFTPClient using libssh2
 * Provides file upload and download functionality for iOS
 */
@OptIn(ExperimentalForeignApi::class)
class Libssh2TransferSftpClient : Libssh2SftpBaseClient(), ITransferFTPClient {

    companion object {
        private const val BUFFER_SIZE = 64 * 1024 // 64KB buffer for optimal SFTP performance
    }

    override suspend fun downloadFile(
        remotePath: String,
        callback: DownloadCallback
    ): Boolean {
        return downloadFileWithResume(remotePath, callback, 0L)
    }

    override suspend fun downloadFileWithResume(
        remotePath: String,
        callback: DownloadCallback,
        resumeOffset: Long
    ): Boolean {
        return try {
            val sftpSession = sftp ?: run {
                val error = IllegalStateException("SFTP session not initialized")
                println(error.message)
                callback.onError(error)
                return false
            }

            // Get file size first
            val fileSize = getFileSize(remotePath)
            if (fileSize < 0) {
                val error = IllegalArgumentException("Failed to get file size for: $remotePath")
                println(error.message)
                callback.onError(error)
                return false
            }

            // Validate resume offset
            if (resumeOffset > fileSize) {
                val error = IllegalArgumentException(
                    "Resume offset ($resumeOffset) exceeds file size ($fileSize)"
                )
                println(error.message)
                callback.onError(error)
                return false
            }

            // Get output stream from callback
            val outputStreamPair = callback.openOutputStream(fileSize, resumeOffset)
            if (outputStreamPair == null) {
                val error = IllegalStateException("Failed to open output stream")
                println(error.message)
                callback.onError(error)
                return false
            }

            val (outputStreamAny, existingFileSize) = outputStreamPair
            val outputStream = outputStreamAny as? NSOutputStream
            if (outputStream == null) {
                val error = IllegalArgumentException("Invalid output stream type")
                println(error.message)
                callback.onError(error)
                return false
            }

            // Determine actual resume offset
            val actualResumeOffset = if (existingFileSize > 0) existingFileSize else resumeOffset

            // Validate actual resume offset
            if (actualResumeOffset > fileSize) {
                val error = IllegalArgumentException(
                    "Existing file size ($actualResumeOffset) exceeds remote file size ($fileSize)"
                )
                println(error.message)
                callback.onError(error)
                return false
            }

            // Open remote file for reading
            val fileHandle = libssh2_sftp_open_ex(
                sftpSession,
                remotePath,
                remotePath.length.convert(),
                LIBSSH2_FXF_READ.convert(),
                0,
                LIBSSH2_SFTP_OPENFILE
            )

            if (fileHandle == null) {
                val error = IllegalStateException("Failed to open remote file: $remotePath")
                println(error.message)
                callback.onError(error)
                return false
            }

            try {
                // Seek to resume offset if needed
                if (actualResumeOffset > 0) {
                    libssh2_sftp_seek64(fileHandle, actualResumeOffset.convert())
                    callback.onProgress(actualResumeOffset, fileSize)
                }

                // Read and write data
                memScoped {
                    val buffer = allocArray<ByteVar>(BUFFER_SIZE)
                    var totalBytesRead = actualResumeOffset

                    while (totalBytesRead < fileSize) {
                        val bytesRead = libssh2_sftp_read(
                            fileHandle,
                            buffer,
                            BUFFER_SIZE.convert()
                        )

                        if (bytesRead < 0) {
                            val error = IllegalStateException(
                                "Error reading from remote file at offset $totalBytesRead"
                            )
                            println(error.message)
                            callback.onError(error)
                            return false
                        }

                        if (bytesRead == 0L) {
                            break // End of file
                        }

                        // Write to output stream
                        val nsData = NSData.create(
                            bytes = buffer,
                            length = bytesRead.convert()
                        )
                        val written = outputStream.write(
                            nsData.bytes?.reinterpret(),
                            maxLength = bytesRead.convert()
                        )

                        if (written < 0) {
                            val error = IllegalStateException("Failed to write to output stream")
                            println(error.message)
                            callback.onError(error)
                            return false
                        }

                        totalBytesRead += bytesRead
                        callback.onProgress(totalBytesRead, fileSize)
                    }

                    // Verify download completion
                    val success = totalBytesRead == fileSize
                    if (success) {
                        callback.onComplete()
                        return true
                    } else {
                        val error = IllegalStateException(
                            "Download incomplete: $totalBytesRead / $fileSize bytes"
                        )
                        println(error.message)
                        callback.onError(error)
                        return false
                    }
                }
            } finally {
                // Always close file handle
                libssh2_sftp_close_handle(fileHandle)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            callback.onError(e)
            false
        }
    }

    override suspend fun uploadFile(
        remotePath: String,
        callback: UploadCallback
    ): Boolean {
        return try {
            println("[SFTP Upload] Starting upload to: $remotePath")
            val sftpSession = sftp ?: run {
                val error = IllegalStateException("SFTP session not initialized")
                println("[SFTP Upload ERROR] ${error.message}")
                callback.onError(error)
                return false
            }

            // Get input stream from callback
            println("[SFTP Upload] Opening input stream...")
            val inputStreamPair = callback.openInputStream()
            if (inputStreamPair == null) {
                val error = IllegalStateException("Failed to open input stream")
                println("[SFTP Upload ERROR] ${error.message}")
                callback.onError(error)
                return false
            }

            val (inputStreamAny, fileSize) = inputStreamPair
            println("[SFTP Upload] File size to upload: $fileSize bytes")
            val inputStream = inputStreamAny as? NSInputStream
            if (inputStream == null) {
                val error = IllegalArgumentException("Invalid input stream type")
                println("[SFTP Upload ERROR] ${error.message}")
                callback.onError(error)
                return false
            }

            // Open remote file for writing
            println("[SFTP Upload] Opening remote file: $remotePath")
            val flags = (LIBSSH2_FXF_WRITE or LIBSSH2_FXF_CREAT or LIBSSH2_FXF_TRUNC).convert<ULong>()
            val mode = (LIBSSH2_SFTP_S_IRUSR or LIBSSH2_SFTP_S_IWUSR or
                       LIBSSH2_SFTP_S_IRGRP or LIBSSH2_SFTP_S_IROTH).convert<Long>()

            val fileHandle = libssh2_sftp_open_ex(
                sftpSession,
                remotePath,
                remotePath.length.convert(),
                flags,
                mode,
                LIBSSH2_SFTP_OPENFILE
            )

            if (fileHandle == null) {
                val error = IllegalStateException("Failed to create remote file: $remotePath")
                println("[SFTP Upload ERROR] ${error.message}")
                callback.onError(error)
                return false
            }
            println("[SFTP Upload] Remote file opened successfully")

            try {
                // Read and write data using a workaround for libssh2_sftp_write
                println("[SFTP Upload] Starting data transfer...")
                memScoped {
                    val buffer = allocArray<ByteVar>(BUFFER_SIZE)
                    var totalBytesWritten = 0L

                    while (true) {
                        val bytesRead = inputStream.read(
                            buffer.reinterpret(),
                            maxLength = BUFFER_SIZE.convert()
                        )

                        if (bytesRead < 0) {
                            val error = IllegalStateException("Error reading from input stream")
                            println("[SFTP Upload ERROR] ${error.message}")
                            callback.onError(error)
                            return false
                        }

                        if (bytesRead == 0L) {
                            println("[SFTP Upload] Reached end of file")
                            break // End of file
                        }

                        println("[SFTP Upload] Read $bytesRead bytes from input stream")

                        // Workaround: Since libssh2_sftp_write has cinterop binding issue,
                        // convert buffer to string (preserving byte values)
                        // This works because we interpret bytes as Latin-1 chars
                        val str = buildString {
                            for (i in 0 until bytesRead.toInt()) {
                                append((buffer[i].toInt() and 0xFF).toChar())
                            }
                        }

                        // Handle partial writes - loop until all bytes are written
                        var offset = 0
                        while (offset < bytesRead.toInt()) {
                            val remaining = str.substring(offset)
                            val bytesWritten = libssh2_sftp_write(
                                fileHandle,
                                remaining,
                                (bytesRead.toInt() - offset).convert()
                            )

                            if (bytesWritten < 0) {
                                val error = IllegalStateException(
                                    "Error writing to remote file at offset $totalBytesWritten"
                                )
                                println("[SFTP Upload ERROR] ${error.message}")
                                callback.onError(error)
                                return false
                            }

                            if (bytesWritten == 0L) {
                                val error = IllegalStateException("Write returned 0 bytes")
                                println("[SFTP Upload ERROR] ${error.message}")
                                callback.onError(error)
                                return false
                            }

                            println("[SFTP Upload] Wrote $bytesWritten bytes (offset: $offset)")
                            offset += bytesWritten.toInt()
                            totalBytesWritten += bytesWritten

                            callback.onProgress(totalBytesWritten, fileSize)
                        }
                    }

                    // Verify upload completion
                    val success = totalBytesWritten == fileSize
                    if (success) {
                        println("[SFTP Upload] Upload verification: SUCCESS ($totalBytesWritten / $fileSize bytes)")
                        callback.onComplete()
                        return true
                    } else {
                        val error = IllegalStateException(
                            "Upload incomplete: $totalBytesWritten / $fileSize bytes"
                        )
                        println("[SFTP Upload ERROR] ${error.message}")
                        callback.onError(error)
                        return false
                    }
                }
            } finally {
                // Always close file handle
                println("[SFTP Upload] Closing file handle")
                libssh2_sftp_close_handle(fileHandle)
            }
        } catch (e: Exception) {
            println("[SFTP Upload ERROR] Exception: ${e.message}")
            e.printStackTrace()
            callback.onError(e)
            false
        }
    }

    override suspend fun getFileSize(remotePath: String): Long {
        return try {
            val sftpSession = sftp ?: return -1L

            memScoped {
                val attrs = alloc<LIBSSH2_SFTP_ATTRIBUTES>()

                // Get file attributes using libssh2_sftp_stat_ex
                val result = libssh2_sftp_stat_ex(
                    sftpSession,
                    remotePath,
                    remotePath.length.convert(),
                    LIBSSH2_SFTP_STAT,
                    attrs.ptr
                )

                if (result == 0) {
                    attrs.filesize.toLong()
                } else {
                    -1L
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }
}

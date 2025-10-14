package com.github.pplong.sftp

import com.github.pplong.sftp.def.FTPFile
import kotlinx.cinterop.*
import libssh2.*

@OptIn(ExperimentalForeignApi::class)
class Libssh2CoreSftpClient : Libssh2SftpBaseClient(), ICoreFTPClient {

    override suspend fun list(path: String): List<FTPFile> {
        val files = mutableListOf<FTPFile>()

        try {
            val sftpSession = sftp ?: return emptyList()

            // Open the directory using libssh2_sftp_open_ex with LIBSSH2_SFTP_OPENDIR flag
            val dirHandle = libssh2_sftp_open_ex(
                sftpSession,
                path,
                path.length.convert(),
                0.convert(),
                0,
                LIBSSH2_SFTP_OPENDIR
            )

            if (dirHandle == null) {
                println("Failed to open directory: $path")
                return emptyList()
            }

            try {
                memScoped {
                    val buffer = allocArray<ByteVar>(512)
                    val attrs = alloc<LIBSSH2_SFTP_ATTRIBUTES>()

                    // Read directory entries using libssh2_sftp_readdir_ex
                    while (true) {
                        val len = libssh2_sftp_readdir_ex(
                            dirHandle,
                            buffer,
                            512.convert(),
                            null,
                            0.convert(),
                            attrs.ptr
                        )

                        if (len <= 0) break

                        val name = buffer.toKString()

                        // Skip "." and ".." entries
                        if (name == "." || name == "..") continue

                        // Convert to FTPFile
                        val ftpFile = convertToFTPFile(name, path, attrs)
                        if (ftpFile != null) {
                            files.add(ftpFile)
                        }
                    }
                }
            } finally {
                // Close the directory handle
                libssh2_sftp_close_handle(dirHandle)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return files
    }

    override suspend fun pwd(): String {
        return try {
            val sftpSession = sftp ?: return ""

            memScoped {
                val buffer = allocArray<ByteVar>(512)

                // Use libssh2_sftp_symlink_ex with LIBSSH2_SFTP_REALPATH flag
                val len = libssh2_sftp_symlink_ex(
                    sftpSession,
                    ".",
                    1.convert(),
                    buffer,
                    512.convert(),
                    LIBSSH2_SFTP_REALPATH
                )

                if (len > 0) {
                    buffer.toKString()
                } else {
                    ""
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Convert libssh2 file attributes to FTPFile
     */
    private fun convertToFTPFile(
        name: String,
        parentPath: String,
        attrs: LIBSSH2_SFTP_ATTRIBUTES
    ): FTPFile? {
        return try {
            val fullPath = if (parentPath.endsWith("/")) {
                "$parentPath$name"
            } else {
                "$parentPath/$name"
            }

            // Check if it's a directory using LIBSSH2_SFTP_S_IFDIR flag
            val permissions = attrs.permissions.toInt()
            val isDirectory = (permissions and LIBSSH2_SFTP_S_IFMT.toInt()) == LIBSSH2_SFTP_S_IFDIR.toInt()

            FTPFile(
                name = name,
                path = fullPath,
                parentPath = parentPath,
                isDirectory = isDirectory,
                size = attrs.filesize.toLong(),
                modifiedTime = attrs.mtime.toLong() * 1000L, // Convert seconds to milliseconds
                permissions = formatPermissions(permissions),
                owner = attrs.uid.toString(),
                group = attrs.gid.toString()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Format file permissions from Unix mode to readable string using libssh2 constants
     * @param mode Unix file mode
     * @return Formatted permission string (e.g., "rwxr-xr-x")
     */
    private fun formatPermissions(mode: Int): String {
        val perms = StringBuilder()

        // Owner permissions
        perms.append(if ((mode and LIBSSH2_SFTP_S_IRUSR.toInt()) != 0) 'r' else '-')
        perms.append(if ((mode and LIBSSH2_SFTP_S_IWUSR.toInt()) != 0) 'w' else '-')
        perms.append(if ((mode and LIBSSH2_SFTP_S_IXUSR.toInt()) != 0) 'x' else '-')

        // Group permissions
        perms.append(if ((mode and LIBSSH2_SFTP_S_IRGRP.toInt()) != 0) 'r' else '-')
        perms.append(if ((mode and LIBSSH2_SFTP_S_IWGRP.toInt()) != 0) 'w' else '-')
        perms.append(if ((mode and LIBSSH2_SFTP_S_IXGRP.toInt()) != 0) 'x' else '-')

        // Other permissions
        perms.append(if ((mode and LIBSSH2_SFTP_S_IROTH.toInt()) != 0) 'r' else '-')
        perms.append(if ((mode and LIBSSH2_SFTP_S_IWOTH.toInt()) != 0) 'w' else '-')
        perms.append(if ((mode and LIBSSH2_SFTP_S_IXOTH.toInt()) != 0) 'x' else '-')

        return perms.toString()
    }
}

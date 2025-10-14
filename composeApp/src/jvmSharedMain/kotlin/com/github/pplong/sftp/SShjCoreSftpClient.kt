package com.github.pplong.sftp

import com.github.pplong.sftp.def.FTPFile
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.xfer.FilePermission

class SShjCoreSftpClient : SshjSftpBaseClient(), ICoreFTPClient {

    override suspend fun list(path: String): List<FTPFile> {
        return sftp.ls(path).mapNotNull { remoteFile ->
            convertToFTPFile(remoteFile, path)
        }
    }

    override suspend fun pwd(): String {
        return sftp.pwd()
    }

    /**
     * Convert SSHJ RemoteResourceInfo to FTPFile
     */
    private fun convertToFTPFile(
        remoteFile: RemoteResourceInfo,
        parentPath: String
    ): FTPFile? {
        return try {
            val name = remoteFile.name

            // Skip "." and ".." entries
            if (name == "." || name == "..") {
                return null
            }

            val attrs = remoteFile.attributes
            val fullPath = if (parentPath.endsWith("/")) {
                "$parentPath$name"
            } else {
                "$parentPath/$name"
            }
            attrs.atime
            FTPFile(
                name = name,
                path = fullPath,
                parentPath = parentPath,
                isDirectory = remoteFile.isDirectory,
                size = attrs.size,
                modifiedTime = attrs.mtime * 1000L, // Convert seconds to milliseconds
                permissions = formatPermissions(attrs.mode),
                owner = attrs.uid.toString(),
                group = attrs.gid.toString()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Format file permissions from Unix mode to readable string using SSHJ's FilePermission
     * @param mode FileMode object from SSHJ
     * @return Formatted permission string (e.g., "rwxr-xr-x")
     */
    private fun formatPermissions(mode: FileMode): String {
        val perms = StringBuilder()

        // Get the permission set from FileMode
        val permSet = mode.permissions

        // Owner permissions
        perms.append(if (permSet.contains(FilePermission.USR_R)) 'r' else '-')
        perms.append(if (permSet.contains(FilePermission.USR_W)) 'w' else '-')
        perms.append(if (permSet.contains(FilePermission.USR_X)) 'x' else '-')

        // Group permissions
        perms.append(if (permSet.contains(FilePermission.GRP_R)) 'r' else '-')
        perms.append(if (permSet.contains(FilePermission.GRP_W)) 'w' else '-')
        perms.append(if (permSet.contains(FilePermission.GRP_X)) 'x' else '-')

        // Other permissions
        perms.append(if (permSet.contains(FilePermission.OTH_R)) 'r' else '-')
        perms.append(if (permSet.contains(FilePermission.OTH_W)) 'w' else '-')
        perms.append(if (permSet.contains(FilePermission.OTH_X)) 'x' else '-')

        return perms.toString()
    }
}
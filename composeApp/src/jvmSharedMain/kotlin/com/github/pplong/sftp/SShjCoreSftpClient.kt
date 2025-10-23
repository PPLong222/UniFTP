package com.github.pplong.sftp

import com.github.pplong.feat.browse.FTPFileUiModel
import com.github.pplong.sftp.def.FTPFile
import kotlinx.coroutines.GlobalScope.coroutineContext
import kotlinx.coroutines.ensureActive
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.xfer.FilePermission

open class SShjCoreSftpClient : SshjSftpBaseClient(), ICoreFTPClient {

    // TODO: figure out why crash when jumping to file select ui
    override suspend fun list(path: String): List<FTPFile> {
        ensureSSHConnection()
        return ssh.newStatefulSFTPClient().use { sftp ->
            sftp.ls(path).mapNotNull { remoteFile ->
                convertToFTPFile(remoteFile, path)
            }
        }
    }

    override suspend fun pwd(): String {
        ensureSSHConnection()
        return ssh.newStatefulSFTPClient().use { sftp ->
            sftp.pwd()
        }
    }

    override suspend fun delete(
        deleteFiles: List<FTPFileUiModel>,
        onProgress: (removedCount: Int) -> Unit
    ) {
        ensureSSHConnection()
        return ssh.newStatefulSFTPClient().use { sftp ->
            deleteFiles.forEachIndexed { index, ftpFile ->
                if (ftpFile.isDirectory) {
                    sftp.rmdir(ftpFile.path)
                } else {
                    sftp.rm(ftpFile.path)
                }
                onProgress(index + 1)
            }
        }
    }

    override suspend fun mkdir(path: String) {
        ensureSSHConnection()
        return ssh.newStatefulSFTPClient().use { sftp ->
            sftp.mkdir(path)
        }
    }

    /**
     * Search for files matching the query string
     * @param query Search query (supports wildcards like *.txt, or partial filename match)
     * @param searchPath Starting directory for search (default is root "/")
     * @return List of matching FTPFile objects
     */
    override suspend fun find(query: String, searchPath: String): List<FTPFile> {
        ensureSSHConnection()
        return ssh.newStatefulSFTPClient().use { sftp ->
            val results = mutableListOf<FTPFile>()

            // Recursive search function
            fun searchDirectory(currentPath: String) {
                try {
                    val files = sftp.ls(currentPath)

                    for (remoteFile in files) {
                        coroutineContext.ensureActive()
                        val name = remoteFile.name

                        // Skip "." and ".." entries
                        if (name == "." || name == "..") continue

                        val ftpFile = convertToFTPFile(remoteFile, currentPath)

                        if (ftpFile != null) {
                            // Check if file matches query
                            if (matchesQuery(name, query)) {
                                results.add(ftpFile)
                            }

                            // Recursively search subdirectories
                            if (remoteFile.isDirectory) {
                                try {
                                    searchDirectory(ftpFile.path)
                                } catch (e: Exception) {
                                    // Skip directories we can't access (permission denied, etc.)
                                    println("[SShjCoreSftpClient] Cannot access directory: ${ftpFile.path}, error: ${e.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("[SShjCoreSftpClient] Error searching directory: $currentPath, error: ${e.message}")
                }
            }

            // Start recursive search from searchPath
            searchDirectory(searchPath)
            results
        }
    }

    /**
     * Check if filename matches query
     * Supports wildcards: * (any characters), ? (single character)
     * Also supports partial string matching (case-insensitive)
     */
    private fun matchesQuery(filename: String, query: String): Boolean {
        if (query.isEmpty()) return true

        // Convert query to regex pattern
        val pattern = when {
            // If query contains wildcards, use pattern matching
            query.contains('*') || query.contains('?') -> {
                val regexPattern = query
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .replace("?", ".")
                Regex(regexPattern, RegexOption.IGNORE_CASE)
            }
            // Otherwise, use simple contains matching (case-insensitive)
            else -> {
                return filename.contains(query, ignoreCase = true)
            }
        }

        return pattern.matches(filename)
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

    private suspend fun ensureSSHConnection() {
        if (!ssh.isConnected) {
            initClient(config)
        }
    }
}
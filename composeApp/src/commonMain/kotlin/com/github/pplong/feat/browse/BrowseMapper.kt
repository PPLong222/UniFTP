package com.github.pplong.feat.browse

import com.github.pplong.sftp.def.FTPFile

fun FTPFile.toFTPFileUiModel(): FTPFileUiModel = FTPFileUiModel(
    name = name,
    path = path,
    parentPath = parentPath,
    isDirectory = isDirectory,
    size = size,
    modifiedTime = modifiedTime,
    permissions = permissions,
    owner = owner,
    group = group
)
package com.github.pplong.feat.transfer.model

import androidx.room.Embedded
import androidx.room.Relation
import com.github.pplong.feat.home.model.FTPServer

data class TransferTaskEmbedded(
    @Embedded
    val task: TransferTask,
    @Relation(
        parentColumn = "serverId",
        entityColumn = "id"
    )
    val server: FTPServer
)

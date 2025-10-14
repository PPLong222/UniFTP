package com.github.pplong.feat.browse.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.pplong.core.def.CommonRequestStatus
import com.github.pplong.feat.browse.BrowseUiState
import com.github.pplong.feat.browse.FTPFileUiModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import uniftp.composeapp.generated.resources.Res
import uniftp.composeapp.generated.resources.ic_folder

@Composable
fun FTPFileInfo(
    file: FTPFileUiModel
) {
    ListItem(
        leadingContent = {
            Icon(
                painter = painterResource(Res.drawable.ic_folder),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        },
        headlineContent = {
            Text(
                file.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Row {
                Text(
                    "Date: ${file.modifiedTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (!file.isDirectory) {
                    Text(
                        "Size: ${file.size}",
                        modifier = Modifier.padding(start = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowseMainContent(
    uiState: BrowseUiState
) {
    when (uiState.requestStatus) {
        CommonRequestStatus.INITIAL, CommonRequestStatus.REQUESTING -> {
            ContainedLoadingIndicator(modifier = Modifier.size(128.dp))
        }

        CommonRequestStatus.SUCCESS -> {
            FTPFileList(uiState)
        }

        CommonRequestStatus.FAILED -> {
            Text("ERROR")
        }
    }
}

@Composable
fun FTPFileList(uiState: BrowseUiState) {
    LazyColumn {
        items(uiState.fileList) { file ->
            FTPFileInfo(file)
        }
    }
}

@Composable
@Preview
fun PreviewBrowseContent() {
    val list = mutableListOf<FTPFileUiModel>()
    repeat(10) {
        list.add(
            FTPFileUiModel(
                name = "TestFile",
                path = "/test/TestFile",
                parentPath = "/test",
                isDirectory = false,
                size = 1024,
                modifiedTime = 0,
                permissions = "0001",
                owner = "root",
                group = "root",
            )
        )
    }
    BrowseMainContent(
        uiState = BrowseUiState(
            fileList = list,
            requestStatus = CommonRequestStatus.SUCCESS
        )
    )
}

@Composable
@Preview
fun PreviewFTPFileInfo() {
    FTPFileInfo(
        file = FTPFileUiModel(
            name = "TestFile",
            path = "/test/TestFile",
            parentPath = "/test",
            isDirectory = false,
            size = 1024,
            modifiedTime = 0,
            permissions = "0001",
            owner = "root",
            group = "root",
        )
    )
}
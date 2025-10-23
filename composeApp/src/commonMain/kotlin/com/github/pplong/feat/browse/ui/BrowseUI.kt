package com.github.pplong.feat.browse.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.pplong.core.def.CommonRequestStatus
import com.github.pplong.core.def.FileType
import com.github.pplong.core.def.toDrawableRes
import com.github.pplong.core.utils.DateUtil
import com.github.pplong.core.utils.FileUtil
import com.github.pplong.feat.browse.BrowseUiIntent
import com.github.pplong.feat.browse.BrowseUiState
import com.github.pplong.feat.browse.FTPFileSelectableUiModel
import com.github.pplong.feat.browse.FTPFileUiModel
import com.github.pplong.feat.home.ui.FTPServerItem
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import uniftp.composeapp.generated.resources.Res
import uniftp.composeapp.generated.resources.ic_check
import uniftp.composeapp.generated.resources.ic_close
import uniftp.composeapp.generated.resources.ic_data_off
import uniftp.composeapp.generated.resources.ic_folder
import uniftp.composeapp.generated.resources.ic_multiple

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FTPFileInfo(
    fileUiModel: FTPFileSelectableUiModel,
    onIntent: (BrowseUiIntent) -> Unit,
    appbarStatus: BrowseToolbarStatus
) {
    val file = fileUiModel.file
    ListItem(
        leadingContent = {
            if (file.isDirectory) {
                Icon(
                    painter = painterResource(Res.drawable.ic_folder),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                FileIcon(file.name)
            }
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
                if (!file.isDirectory) {
                    Text(
                        FileUtil.getFileSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    DateUtil.getFormatDate(file.modifiedTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        trailingContent = {
            AnimatedContent(
                targetState = fileUiModel.status::class,
                modifier = Modifier.size(32.dp)
            ) { target ->
                when (target) {
                    BrowseFileLoadingStatus.Checked::class, BrowseFileLoadingStatus.UnChecked::class -> {
                        Checkbox(
                            checked = fileUiModel.status == BrowseFileLoadingStatus.Checked,
                            onCheckedChange = { checked ->
                                onIntent(BrowseUiIntent.SelectFile(file, checked))
                            }
                        )
                    }

                    BrowseFileLoadingStatus.Failed::class -> {
                        Icon(
                            painter = painterResource(Res.drawable.ic_data_off),
                            modifier = Modifier.size(32.dp),
                            contentDescription = null
                        )
                    }

                    BrowseFileLoadingStatus.Loading::class -> {
                        val percent =
                            (fileUiModel.status as? BrowseFileLoadingStatus.Loading)?.percent ?: 0f
                        CircularWavyProgressIndicator(
                            progress = { percent },
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    BrowseFileLoadingStatus.None::class -> {

                    }

                    BrowseFileLoadingStatus.Success::class -> {
                        Icon(
                            painter = painterResource(Res.drawable.ic_check),
                            modifier = Modifier.size(32.dp),
                            contentDescription = null
                        )
                    }

                    BrowseFileLoadingStatus.Waiting::class -> {
                        ContainedLoadingIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }
        },
        modifier = Modifier.clickable {
            if (fileUiModel.status == BrowseFileLoadingStatus.UnChecked || fileUiModel.status == BrowseFileLoadingStatus.Checked) {
                onIntent(BrowseUiIntent.SelectFile(file,
                    fileUiModel.status != BrowseFileLoadingStatus.Checked
                ))
            } else if (file.isDirectory) {
                onIntent(BrowseUiIntent.Jump(file.path))
            }
        }
    )
}

@Composable
fun FileIcon(fileName: String) {
    Icon(
        painter = painterResource(FileType.fromFileName(fileName).toDrawableRes()),
        contentDescription = null,
        modifier = Modifier.size(48.dp)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowseMainContent(
    uiState: BrowseUiState,
    onIntent: (BrowseUiIntent) -> Unit
) {
    when (uiState.requestStatus) {
        CommonRequestStatus.INITIAL, CommonRequestStatus.REQUESTING -> {

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                ContainedLoadingIndicator(modifier = Modifier.padding(top = 100.dp).size(108.dp))
            }
        }

        CommonRequestStatus.SUCCESS -> {
            FTPFileList(uiState, onIntent)
        }

        CommonRequestStatus.FAILED -> {
            Text("ERROR")
        }
    }
}

@Composable
fun FTPFileList(uiState: BrowseUiState, onIntent: (BrowseUiIntent) -> Unit) {
    Box {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(uiState.fileList) { file ->
                FTPFileInfo(
                    file,
                    onIntent,
                    uiState.toolbarStatus,
                )
            }
        }

        BrowseFloatingActionMenu(
            modifier = Modifier.align(Alignment.BottomEnd),
            barStatus = uiState.toolbarStatus,
            onIntent = onIntent,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowseTopAppBar(
    headlineText: String,
    subtitleText: String? = null,
    appbarStatus: BrowseToolbarStatus,
    scrollBehavior: TopAppBarScrollBehavior,
    onIntent: (BrowseUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {

    TopAppBar(
        title = {
            Text(headlineText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        subtitle = {
            if (subtitleText != null) Text(
                subtitleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            ) else null
        },
        navigationIcon = {

        },
        actions = {
            IconButton(
                onClick = {
                    onIntent(BrowseUiIntent.ChangeBrowseMode(appbarStatus))
                }
            ) {
                Icon(
                    painter = painterResource(if (appbarStatus == BrowseToolbarStatus.STANDARD) Res.drawable.ic_multiple else Res.drawable.ic_close),
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun PreviewBrowseTopBar() {
    BrowseTopAppBar(
        "Headline",
        null,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
        onIntent = {},
        appbarStatus = BrowseToolbarStatus.STANDARD
    )
}

@Composable
@Preview
fun PreviewBrowseContent() {
    val list = mutableListOf<FTPFileSelectableUiModel>()
    repeat(10) {
        list.add(
            FTPFileSelectableUiModel(
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
        )
    }
    BrowseMainContent(
        uiState = BrowseUiState(
            fileList = list,
            requestStatus = CommonRequestStatus.SUCCESS,
            server = FTPServerItem(
                host = "TODO()",
                password = "22",
                user = "user",
                port = 22,
                nickname = "nickname",
                lastConnectedTime = 0L,
                id = 0
            )
        ),
        {}
    )
}

@Composable
@Preview
fun PreviewFTPFileInfo() {
    FTPFileInfo(
        fileUiModel = FTPFileSelectableUiModel(
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
        ),
        {},
        appbarStatus = BrowseToolbarStatus.SELECTED
    )
}

@Composable
@Preview
fun PreviewFTPFileInfoWithDownloading() {
    FTPFileInfo(
        fileUiModel = FTPFileSelectableUiModel(
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
            ),
            status = BrowseFileLoadingStatus.Loading(0.4F)
        ),
        {},
        appbarStatus = BrowseToolbarStatus.SELECTED
    )
}

@Composable
@Preview
fun PreviewFTPFileInfoWithCheck() {
    FTPFileInfo(
        fileUiModel = FTPFileSelectableUiModel(
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
            ),
            status = BrowseFileLoadingStatus.Success
        ),
        {},
        appbarStatus = BrowseToolbarStatus.SELECTED
    )
}
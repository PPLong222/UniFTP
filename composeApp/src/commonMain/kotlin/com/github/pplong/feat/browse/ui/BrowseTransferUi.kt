package com.github.pplong.feat.browse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.pplong.core.utils.FileUtil
import com.github.pplong.feat.browse.BrowseUiIntent
import com.github.pplong.feat.browse.FTPFileTransferringUiModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uniftp.composeapp.generated.resources.Res
import uniftp.composeapp.generated.resources.completed
import uniftp.composeapp.generated.resources.downloading
import uniftp.composeapp.generated.resources.ic_folder
import uniftp.composeapp.generated.resources.uploading

@Composable
fun BrowseTransferFloatingStatusButton(modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.padding(start = 16.dp, bottom = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("Transfer")
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowseTransferBottomSheet(
    onDismiss: () -> Unit,
    transferringList: List<FTPFileTransferringUiModel>,
    transferredList: List<FTPFileTransferringUiModel>

) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val pagerState = rememberPagerState(
        pageCount = { BrowseTransferType.entries.size }
    )

    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            Modifier.padding(horizontal = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            BrowseTransferType.entries.forEachIndexed { index, selection ->
                ToggleButton(
                    checked = pagerState.currentPage == index,
                    onCheckedChange = {
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shapes =
                        when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            BrowseSearchBarDirectorSelection.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                ) {
                    Text(stringResource(selection.titleRes))
                }
            }
        }



        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            verticalAlignment = Alignment.Top
        ) { page ->
            val type =
                if (page == 0) BrowseTransferType.DOWNLOAD else BrowseTransferType.UPLOAD
            val transferringTypeList = transferringList.filter { it.type == type }
            val transferredTypeList = transferredList.filter { it.type == type }
            LazyColumn {
                item {
                    Text(
                        stringResource(if (page == 0) Res.string.downloading else Res.string.uploading),
                        modifier = Modifier.padding(bottom = 8.dp, start = 12.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                items(transferringTypeList) { file ->
                    FTPFileTransferringItem(file, {})
                }
                item {
                    Text(
                        stringResource(Res.string.completed),
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                items(transferredTypeList) { file ->
                    FTPFileTransferredItem(file, {})
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FTPFileTransferringItem(
    fileUiModel: FTPFileTransferringUiModel,
    onIntent: (BrowseUiIntent) -> Unit
) {
    val file = fileUiModel.file
    Column {
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        FileUtil.getFileSize(fileUiModel.speed).plus("/s"),
                        style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(progress = { fileUiModel.progress })
                }
            },
            trailingContent = {

            },
            modifier = Modifier.clickable {

            }
        )
        HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FTPFileTransferredItem(
    fileUiModel: FTPFileTransferringUiModel,
    onIntent: (BrowseUiIntent) -> Unit
) {
    val file = fileUiModel.file
    Column {
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

            },
            trailingContent = {

            },
            modifier = Modifier.clickable {

            }
        )
        HorizontalDivider(modifier = Modifier.padding(start = 64.dp))
    }
}
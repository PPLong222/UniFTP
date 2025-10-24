package com.github.pplong.feat.browse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.pplong.feat.browse.BrowseUiIntent
import com.github.pplong.feat.browse.FTPFileTransferringUiModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uniftp.composeapp.generated.resources.Res
import uniftp.composeapp.generated.resources.ic_folder

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
    transferringList: List<FTPFileTransferringUiModel>
) {
    var selectedIndex by remember { mutableStateOf(0) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Row(
            Modifier.padding(horizontal = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            BrowseTransferType.entries.forEachIndexed { index, selection ->
                ToggleButton(
                    checked = selectedIndex == index,
                    onCheckedChange = {
                        selectedIndex = index
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

        LazyColumn {
            items(transferringList) { file ->
                FTPFileTransferringItem(file, {})
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
                LinearWavyProgressIndicator(progress = { fileUiModel.progress })
            }
        },
        trailingContent = {

        },
        modifier = Modifier.clickable {

        }
    )

}
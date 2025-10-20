package com.github.pplong.feat.browse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.github.pplong.feat.browse.BrowseDialogState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import uniftp.composeapp.generated.resources.Res
import uniftp.composeapp.generated.resources.cancel
import uniftp.composeapp.generated.resources.confirm
import uniftp.composeapp.generated.resources.delete_files
import uniftp.composeapp.generated.resources.file_name_already_exist_title
import uniftp.composeapp.generated.resources.ic_delete
import uniftp.composeapp.generated.resources.ic_warning
import uniftp.composeapp.generated.resources.remove_dialog_tip
import uniftp.composeapp.generated.resources.removing_with_placeholder
import uniftp.composeapp.generated.resources.replace_all
import uniftp.composeapp.generated.resources.replace_dialog_tip

private enum class BrowseState {
    CONFIRM,
    DELETING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseDeleteDialog(
    onDismiss: () -> Unit,
    dialogState: BrowseDialogState.ConfirmDelete,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    var state by remember { mutableStateOf(BrowseState.CONFIRM) }
    when (state) {
        BrowseState.CONFIRM -> BrowseDeleteConfirmAlertDialog(
            dialogState.fileList.size,
            onConfirm = {
                state = BrowseState.DELETING
                onConfirm()
            },
            onDismiss
        )

        BrowseState.DELETING -> BrowseDeleteDeletingAlertDialog(
            dialogState.fileList.size,
            dialogState.deleteCount,
            onCancel
        )
    }

}

@Composable
private fun BrowseDeleteConfirmAlertDialog(
    fileCount: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            Text(
                text = stringResource(Res.string.confirm),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { onConfirm() }
            )
        },
        dismissButton = {
            Text(
                text = stringResource(Res.string.cancel),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { onCancel() }
            )
        },
        icon = {
            Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = null)
        },
        title = {
            Text(text = stringResource(Res.string.delete_files, fileCount))
        },
        text = {
            Text(text = stringResource(Res.string.remove_dialog_tip, fileCount))
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BrowseDeleteDeletingAlertDialog(
    fileCount: Int,
    removedCount: Int,
    onCancel: () -> Unit
) {
    AlertDialog(
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        onDismissRequest = onCancel,
        confirmButton = {},
        dismissButton = {
            Text(
                text = stringResource(Res.string.cancel),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { onCancel() }
            )
        },
        icon = {
            Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = null)
        },
        title = {
            Text(text = stringResource(Res.string.delete_files, fileCount))
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        Res.string.removing_with_placeholder,
                        removedCount,
                        fileCount
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                ContainedLoadingIndicator(
                    modifier = Modifier
                        .size(32.dp)
                )
            }
        }
    )
}

@Composable
fun BrowseUploadSameNameAlertDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        onDismissRequest = onDismiss,
        dismissButton = {
            Text(text = stringResource(Res.string.cancel), modifier = Modifier.clickable {
                onDismiss()
            })
        },
        confirmButton = {
            Text(text = stringResource(Res.string.replace_all), modifier = Modifier.clickable {
                onDismiss()
                onConfirm()
            })
        },
        icon = {
            Icon(painter = painterResource(Res.drawable.ic_warning), contentDescription = null)
        },
        title = {
            Text(text = stringResource(Res.string.file_name_already_exist_title))
        },
        text = {
            Text(text = stringResource(Res.string.replace_dialog_tip))
        }
    )
}


@Preview
@Composable
fun PreviewBrowseDeleteDialog() {
    BrowseDeleteDialog(
        onDismiss = {},
        dialogState = BrowseDialogState.ConfirmDelete(emptyList(), 10),
        onConfirm = {},
        onCancel = {}
    )
}

@Preview
@Composable
private fun PreviewBrowseDeleteConfirmDialogContent() {
    BrowseDeleteConfirmAlertDialog(10, {}, {})
}


@Preview
@Composable
private fun PreviewBrowseDeleteDeletingDialogContent() {
    BrowseDeleteDeletingAlertDialog(10, 5, {})
}


@Preview
@Composable
fun PreviewBrowseUploadSameNameAlertDialog() {
    BrowseUploadSameNameAlertDialog(
        onDismiss = {},
        onConfirm = {}
    )
}
package com.github.pplong.feat.home.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.pplong.core.api.rememberDirectoryPicker
import com.github.pplong.core.def.CommonRequestStatus
import com.github.pplong.core.def.ServerPortInfo
import com.github.pplong.core.widgets.PasswordTextField
import com.github.pplong.feat.home.EditServerState
import com.github.pplong.feat.home.HomeUiIntent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import uniftp.composeapp.generated.resources.Res
import uniftp.composeapp.generated.resources.congratulations
import uniftp.composeapp.generated.resources.ic_file_open
import uniftp.composeapp.generated.resources.ic_thumb_up
import uniftp.composeapp.generated.resources.network_error_tip
import uniftp.composeapp.generated.resources.next
import uniftp.composeapp.generated.resources.nickname
import uniftp.composeapp.generated.resources.path_with_placeholder
import uniftp.composeapp.generated.resources.port
import uniftp.composeapp.generated.resources.test_again
import uniftp.composeapp.generated.resources.test_connectivity
import uniftp.composeapp.generated.resources.user

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditFTPServerBottomSheet(
    onDismissRequest: () -> Unit = {},
    editServerState: EditServerState,
    onIntent: (HomeUiIntent) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        AnimatedContent(editServerState.configureState) { state ->
            when (state) {
                EditConfigureState.CONNECTING -> EditFTPServerBottomSheetContent(
                    editServerState,
                    onIntent
                )

                EditConfigureState.CONFIGURING -> EditFTPServerConfigureBottomSheetContent(
                    editServerState,
                    onIntent
                )
            }
        }

    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EditFTPServerBottomSheetContent(
    editServerState: EditServerState,
    onIntent: (HomeUiIntent) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        Text(
            style = MaterialTheme.typography.titleLarge,
            text = "Add FTP Server",
            modifier = Modifier.padding()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = editServerState.server.host,
            onValueChange = {
                onIntent(
                    HomeUiIntent.OnChangeServerInfo(
                        editServerState.server.copy(
                            host = it
                        )
                    )
                )
            },
            label = { Text("Host") },
            modifier = Modifier
                .fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        PasswordTextField(
            text = editServerState.server.password,
            onValueChange = {
                onIntent(
                    HomeUiIntent.OnChangeServerInfo(
                        editServerState.server.copy(
                            password = it
                        )
                    )
                )
            },
            labelString = "Password",
            modifier = Modifier
                .fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row {
            OutlinedTextField(
                value = editServerState.server.user,
                onValueChange = {
                    onIntent(
                        HomeUiIntent.OnChangeServerInfo(
                            editServerState.server.copy(
                                user = it
                            )
                        )
                    )
                },
                label = { Text(stringResource(Res.string.user)) },
                modifier = Modifier
                    .weight(0.7f)
            )
            OutlinedTextField(
                value = editServerState.server.port.toString(),
                onValueChange = { portStr ->
                    val newPort = portStr.toIntOrNull()
                        ?.coerceIn(ServerPortInfo.MIN_PORT, ServerPortInfo.MAX_PORT)
                        ?: 0
                    onIntent(HomeUiIntent.OnChangeServerInfo(editServerState.server.copy(port = newPort)))
                },
                label = { Text(stringResource(Res.string.port)) },
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(0.3f),
                supportingText = {
                    Text("${ServerPortInfo.MIN_PORT}-${ServerPortInfo.MAX_PORT}")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                singleLine = true
            )
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = editServerState.status,
            transitionSpec = {
                fadeIn(
                    animationSpec = tween(1000)
                ) togetherWith fadeOut(animationSpec = tween(400))
            },
            label = "",
        ) { targetState ->
            when (targetState) {
                CommonRequestStatus.FAILED -> {
                    // TODO: tips of multiple kinds
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(Res.string.network_error_tip),
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = { onIntent(HomeUiIntent.TestConnectivity) },
                        ) {
                            Text(stringResource(Res.string.test_again))
                        }
                    }

                }

                CommonRequestStatus.INITIAL ->
                    Button(
                        onClick = { onIntent(HomeUiIntent.TestConnectivity) },
                    ) {
                        Text(stringResource(Res.string.test_connectivity))
                    }


                CommonRequestStatus.REQUESTING ->
                    LoadingIndicator()

                CommonRequestStatus.SUCCESS -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row {
                            Icon(
                                painter = painterResource(Res.drawable.ic_thumb_up),
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(Res.string.congratulations),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { onIntent(HomeUiIntent.NextToConfigure) },
                        ) {
                            Text(stringResource(Res.string.next))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EditFTPServerConfigureBottomSheetContent(
    editServerState: EditServerState,
    onIntent: (HomeUiIntent) -> Unit,
) {
    val directoryPicker = rememberDirectoryPicker { selectedPath ->
        selectedPath?.let {
            onIntent(
                HomeUiIntent.OnChangeServerInfo(
                    editServerState.server.copy(downloadDir = it)
                )
            )
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        Text(
            style = MaterialTheme.typography.titleLarge,
            text = "Customize",
        )

        Text(
            style = MaterialTheme.typography.titleMedium,
            text = "Pick an Icon",
        )

        OutlinedTextField(
            value = editServerState.server.nickname,
            onValueChange = {
                onIntent(
                    HomeUiIntent.OnChangeServerInfo(
                        editServerState.server.copy(
                            nickname = it
                        )
                    )
                )
            },
            label = { Text(stringResource(Res.string.nickname)) },
            modifier = Modifier
                .fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Customize download path")
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                directoryPicker()
            }, shape = RoundedCornerShape(size = 8.dp)) {
                Icon(
                    painter = painterResource(Res.drawable.ic_file_open),
                    contentDescription = null
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(
                    Res.string.path_with_placeholder,
                    editServerState.server.downloadDir ?: ""
                ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }


        Button(
            onClick = { onIntent(HomeUiIntent.SaveServer) },
            modifier = Modifier.padding(12.dp)
        ) {
            Text("Save")
        }
    }
}

@Preview
@Composable
internal fun PreviewConfigureServerBottomSheetContent() {
    EditFTPServerConfigureBottomSheetContent(
        editServerState = EditServerState(
            server = EditFTPServerItem(),
        ),
        onIntent = {}
    )
}

@Preview
@Composable
internal fun PreviewEditFTPServerBottomSheetContent() {
    EditFTPServerBottomSheetContent(
        editServerState = EditServerState(
            server = EditFTPServerItem(),
        ),
        onIntent = {}
    )
}
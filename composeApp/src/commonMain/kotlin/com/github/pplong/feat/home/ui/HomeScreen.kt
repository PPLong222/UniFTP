package com.github.pplong.feat.home.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.github.pplong.BrowseScreenNav
import com.github.pplong.feat.home.HomeUiEffect
import com.github.pplong.feat.home.HomeUiIntent
import com.github.pplong.feat.home.viewmodel.HomeViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import uniftp.composeapp.generated.resources.Res
import uniftp.composeapp.generated.resources.ic_add
import uniftp.composeapp.generated.resources.ic_server
import uniftp.composeapp.generated.resources.user_with_host

@Preview
@Composable
fun HomeScreen(navController: NavHostController) {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is HomeUiEffect.NavigateToBrowser -> {
                    navController.navigate(
                        BrowseScreenNav(
                            effect.server.nickname,
                            effect.server.host,
                            effect.server.user,
                            effect.server.port,
                            effect.server.password,
                            effect.server.downloadDir
                        )
                    )
                }

                else -> {}
            }
        }
    }

    if (state.isEditDialogVisible) {
        EditFTPServerBottomSheet(
            onDismissRequest = { viewModel.sendIntent(HomeUiIntent.DismissEditDialog) },
            editServerState = state.editServerState,
            onIntent = viewModel::sendIntent
        )
    }

    Scaffold(floatingActionButton = {
        FloatingActionButton(
            onClick = { viewModel.sendIntent(HomeUiIntent.ShowAddServerDialog) },
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_add),
                contentDescription = null
            )
        }
    }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            HostPage(state.serverList, viewModel::sendIntent)
        }
    }
}


@Composable
fun HostPage(
    list: List<FTPServerItem>,
    onIntent: (HomeUiIntent) -> Unit,
) {
    if (list.isEmpty()) {
        Text("None")
    } else {
        LazyColumn() {
            items(list) { server ->
                HostItem(server, onIntent)
            }
        }
    }
}

@Composable
fun HostItem(
    server: FTPServerItem,
    onIntent: (HomeUiIntent) -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.padding(4.dp)
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    painter = painterResource(Res.drawable.ic_server),
                    contentDescription = null
                )
            },
            headlineContent = {
                Text(
                    server.nickname ?: stringResource(
                        Res.string.user_with_host,
                        server.user,
                        server.host
                    ), style = MaterialTheme.typography.titleMedium
                )
            },
            overlineContent = {},
            supportingContent = {
                Column {
                    Text(
                        "User: ${server.user}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            onIntent(HomeUiIntent.Connect(server))
                        },
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .width(48.dp)
                    ) {
                    }
                }

            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
        )
    }
}

@Preview
@Composable
fun PreviewHostItem() {
    HostItem(
        server = FTPServerItem(0, "127.0.0.1", "123456", "root", 22, "test", 0),
        onIntent = {}
    )
}


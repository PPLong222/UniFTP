package com.github.pplong.feat.browse.ui

import BrowseViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.github.pplong.core.utils.rememberFilePicker
import com.github.pplong.feat.browse.BrowseDialogState
import com.github.pplong.feat.browse.BrowseUiEffect
import com.github.pplong.feat.browse.BrowseUiIntent
import com.github.pplong.feat.home.ui.FTPServerItem
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun BrowseScreen(
    server: FTPServerItem,
    navController: NavHostController
) {
    val viewModel = koinViewModel<BrowseViewModel>(parameters = { parametersOf(server) })
    val state by viewModel.uiState.collectAsState()
    // File picker for upload
    val filePicker = rememberFilePicker { results ->
        results?.let { fileList ->
            // Convert FilePickerResult to upload format (uri, fileName)
            val files = fileList.map { it.uri to it.name }
            viewModel.sendIntent(BrowseUiIntent.UploadFiles(files))
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { uiEffect ->
            when (uiEffect) {
                BrowseUiEffect.ShowUploadPicker -> {
                    filePicker()
                }
            }
        }
    }

    BackHandler {
        viewModel.sendIntent(BrowseUiIntent.Back)
    }
    Scaffold(
        topBar = {
            BrowseTopAppBar(
                state.server.nickname ?: state.server.host.plus("@").plus(state.server.user),
                subtitleText = if (state.server.nickname == null) {
                    null
                } else {
                    state.server.user.plus("@").plus(state.server.host)
                },
                scrollBehavior = scrollBehavior,
                appbarStatus = state.toolbarStatus,
                onIntent = viewModel::sendIntent,
            )
        },
        floatingActionButton = {
            BrowseFloatingToolbar(
                barStatus = state.toolbarStatus,
                onIntent = viewModel::sendIntent
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            DraggablePathIndicator(
                state.path,
                { newPath -> viewModel.sendIntent(BrowseUiIntent.Jump(newPath)) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                BrowseMainContent(state, viewModel::sendIntent)
            }
        }
    }

    when (val dialogState = state.dialogState) {
        BrowseDialogState.None -> {}
        is BrowseDialogState.ConfirmDelete -> {
            BrowseDeleteDialog(
                { viewModel.sendIntent(BrowseUiIntent.DismissDialog) },
                dialogState,
                { viewModel.sendIntent(BrowseUiIntent.Delete) },
                { viewModel.sendIntent(BrowseUiIntent.DismissDialog) },
            )
        }
    }
}
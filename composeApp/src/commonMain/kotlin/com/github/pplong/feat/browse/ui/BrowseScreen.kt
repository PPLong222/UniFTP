package com.github.pplong.feat.browse.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.pplong.feat.browse.viewmodel.BrowseViewModel
import com.github.pplong.koinViewModel

@Composable
fun BrowseScreen() {
    val viewModel = koinViewModel<BrowseViewModel>()
    val state by viewModel.uiState.collectAsState()
    Scaffold {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            BrowseMainContent(state)
        }
    }
}
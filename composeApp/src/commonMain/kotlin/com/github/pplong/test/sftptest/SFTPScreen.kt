package com.github.pplong.test.sftptest

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SFTPScreen() {
    val viewModel = koinViewModel<SFTPTestViewModel>()
    Column {
        Spacer(modifier = Modifier.height(50.dp))
        Button(onClick = {
            viewModel.test()
        }) {
            Text("Test")
        }

        Button(onClick = {

        }) {}
    }
}
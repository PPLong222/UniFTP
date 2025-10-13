package com.github.pplong.test.sftptest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.pplong.sftp.api.IUSftpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class SFTPTestViewModel(
    val client: IUSftpClient
): ViewModel() {
    fun test() {
        viewModelScope.launch(Dispatchers.IO) {
            client.connect("185.211.4.19", 22)
            client.auth("sftpuser", "88888888")
        }
    }
}
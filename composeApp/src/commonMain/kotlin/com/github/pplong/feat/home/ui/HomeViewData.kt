package com.github.pplong.feat.home.ui

data class EditFTPServerItem (
    val host: String = "",
    val password: String = "",
    val authByPublicKey: Boolean = false,
    val publicKeyUri: String = "",
    val paraphrase: String = "",
    val usePhrase: Boolean = false,
    val user: String = "",
    val port: Int = 22,
    val nickname: String = "",
    val lastConnectedTime: Long = 0,
    val downloadDir: String? = null,
    val useDefaultDownloadDir: Boolean = true,
    val defaultDownloadDir: String = "",
    val keyName: String = ""
) {
    private val passValid = (!authByPublicKey && password.isNotEmpty())
            || (authByPublicKey && publicKeyUri.isNotEmpty() && (!usePhrase || paraphrase.isNotEmpty()))
    private val downloadDirValid = useDefaultDownloadDir || downloadDir != null

    val testConnectivityEnabled = host.isNotEmpty()
            && user.isNotEmpty()
            && port > 0
            && passValid

    val saveEnabled = nickname.isNotEmpty() && downloadDirValid
}

enum class EditConfigureState {
    CONNECTING,
    CONFIGURING
}
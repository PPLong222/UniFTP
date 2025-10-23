package com.github.pplong.core.def

enum class CommonRequestStatus {
    INITIAL,
    REQUESTING,
    SUCCESS,
    FAILED;

    fun isLoading() = this == REQUESTING

    fun isSuccess() = this == SUCCESS
}
package com.github.pplong.core.utils

import androidx.compose.runtime.Composable

/**
 * Platform-specific directory picker interface.
 * Returns a lambda that launches the directory picker when invoked.
 */
@Composable
expect fun rememberDirectoryPicker(
    onDirectorySelected: (String?) -> Unit
): () -> Unit

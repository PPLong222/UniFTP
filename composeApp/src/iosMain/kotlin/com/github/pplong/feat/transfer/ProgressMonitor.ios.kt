package com.github.pplong.feat.transfer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * iOS implementation using URLSession or BGTaskScheduler
 * TODO: Implement iOS-specific progress monitoring
 */
actual class ProgressMonitor {

    /**
     * Observe all active transfer tasks' progress
     * Currently returns empty flow - to be implemented with iOS background transfer APIs
     */
    actual fun observeAllProgress(): Flow<List<ProgressUpdate>> {
        // TODO: Implement iOS progress monitoring using:
        // - URLSession.downloadTask progress observation
        // - Or custom progress tracking mechanism
        return flowOf(emptyList())
    }
}

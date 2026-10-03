package com.vedica.labs.ind.app.docora.core.model

/** Persisted application settings, mirrored from DataStore (PRD §50). */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    /** Accessibility: shortens every animation instead of removing it, so state stays visible. */
    val reduceMotion: Boolean = false,
    val defaultViewMode: ViewMode = ViewMode.default,
    val defaultSortOrder: SortOrder = SortOrder.default,
    val defaultSortDirection: SortDirection = SortDirection.DESCENDING,
    val appLockEnabled: Boolean = false,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.IMMEDIATELY,
    val secureScreenEnabled: Boolean = false,
    val encryptExtractedText: Boolean = false,
    val scannerQuality: ScannerQuality = ScannerQuality.default,
    val scannerAutoCapture: Boolean = true,
    val scannerMode: ScanMode = ScanMode.default,
    val scannerFilter: ScanFilter = ScanFilter.default,
    val ocrAfterScan: Boolean = true,
    val trashRetention: TrashRetention = TrashRetention.THIRTY_DAYS,
    val thumbnailCacheLimitMb: Int = 128,
    val indexOcrText: Boolean = true,
    val indexDocumentText: Boolean = true,
    val onboardingCompleted: Boolean = false,
    /** Most recent search terms, newest first; bounded by [SearchRepository.MAX_RECENT_SEARCHES]. */
    val recentSearches: List<String> = emptyList(),
) {
    companion object {
        /** The thumbnail cache never grows past this hard ceiling, whatever the setting. */
        const val MAX_THUMBNAIL_CACHE_MB: Int = 512

        val Default = AppSettings()
    }
}

/** One option in a settings selector. */
data class SettingsOption<T>(
    val value: T,
    val titleKey: String,
)

package com.vedica.labs.ind.app.docora.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.backup.BackupRepository
import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.common.DocoraLog
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.datastore.PreferencesManager
import com.vedica.labs.ind.app.docora.core.files.ThumbnailStore
import com.vedica.labs.ind.app.docora.core.model.AppSettings
import com.vedica.labs.ind.app.docora.core.model.AutoLockTimeout
import com.vedica.labs.ind.app.docora.core.model.DashboardStats
import com.vedica.labs.ind.app.docora.core.model.ScanFilter
import com.vedica.labs.ind.app.docora.core.model.ScanMode
import com.vedica.labs.ind.app.docora.core.model.ScannerQuality
import com.vedica.labs.ind.app.docora.core.model.SmartCollection
import com.vedica.labs.ind.app.docora.core.model.SortDirection
import com.vedica.labs.ind.app.docora.core.model.SortOrder
import com.vedica.labs.ind.app.docora.core.model.ThemeMode
import com.vedica.labs.ind.app.docora.core.model.TrashRetention
import com.vedica.labs.ind.app.docora.core.model.ViewMode
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.repository.SearchRepository
import com.vedica.labs.ind.app.docora.core.security.AppLockManager
import com.vedica.labs.ind.app.docora.core.security.AuthenticationResult
import com.vedica.labs.ind.app.docora.core.security.BiometricAuthenticator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val documentRepository: DocumentRepository,
    private val searchRepository: SearchRepository,
    private val backupRepository: BackupRepository,
    private val thumbnailStore: ThumbnailStore,
    private val biometricAuthenticator: BiometricAuthenticator,
    private val appLockManager: AppLockManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = preferencesManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.Default)

    private val _message = MutableStateFlow<SettingsMessage?>(null)
    val message: StateFlow<SettingsMessage?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(SettingsBusy.NONE)
    val busy: StateFlow<SettingsBusy> = _busy.asStateFlow()

    /** 0..1 fraction while the search index is being rebuilt, null otherwise. */
    private val _reindexProgress = MutableStateFlow<Float?>(null)
    val reindexProgress: StateFlow<Float?> = _reindexProgress.asStateFlow()

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    private val _dashboard = MutableStateFlow(DashboardStats.Empty)
    val dashboard: StateFlow<DashboardStats> = _dashboard.asStateFlow()

    val canUseBiometrics: Boolean get() = biometricAuthenticator.canAuthenticate()
    val hasEnrolledBiometric: Boolean get() = biometricAuthenticator.hasEnrolledBiometric()

    fun refreshStats() {
        viewModelScope.launch {
            _cacheBytes.value = runCatching { thumbnailStore.cacheSizeBytes() }.getOrDefault(0L)
        }
        viewModelScope.launch {
            runCatching { documentRepository.observeDashboard().first() }
                .onSuccess { _dashboard.value = it }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun setTheme(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setDynamicColor(enabled: Boolean) = update { it.copy(useDynamicColor = enabled) }
    fun setReduceMotion(enabled: Boolean) = update { it.copy(reduceMotion = enabled) }
    fun setViewMode(mode: ViewMode) = update { it.copy(defaultViewMode = mode) }
    fun setSort(order: SortOrder, direction: SortDirection) =
        update { it.copy(defaultSortOrder = order, defaultSortDirection = direction) }

    fun setAutoLock(timeout: AutoLockTimeout) = update { it.copy(autoLockTimeout = timeout) }
    fun setSecureScreen(enabled: Boolean) = update { it.copy(secureScreenEnabled = enabled) }

    fun setScannerQuality(quality: ScannerQuality) = update { it.copy(scannerQuality = quality) }
    fun setScannerAutoCapture(enabled: Boolean) = update { it.copy(scannerAutoCapture = enabled) }
    fun setScannerMode(mode: ScanMode) = update { it.copy(scannerMode = mode) }
    fun setScannerFilter(filter: ScanFilter) = update { it.copy(scannerFilter = filter) }

    fun setTrashRetention(retention: TrashRetention) = update { it.copy(trashRetention = retention) }
    fun setThumbnailCacheLimit(mb: Int) = update {
        it.copy(
            thumbnailCacheLimitMb = mb.coerceIn(
                ThumbnailCacheLimits.MIN_MB,
                AppSettings.MAX_THUMBNAIL_CACHE_MB,
            ),
        )
    }

    fun clearThumbnailCache() {
        if (_busy.value != SettingsBusy.NONE) return
        _busy.value = SettingsBusy.WORKING
        viewModelScope.launch {
            val cleared = runCatching { thumbnailStore.clear() }.getOrDefault(0)
            _cacheBytes.value = runCatching { thumbnailStore.cacheSizeBytes() }.getOrDefault(0L)
            _busy.value = SettingsBusy.NONE
            _message.value = SettingsMessage(R.string.settings_cache_cleared_files, cleared)
            DocoraLog.i(TAG, "cache_cleared", "files" to cleared)
        }
    }

    /**
     * Rebuilds the full-text index from the live library: clears the FTS table, then writes one
     * row per document through [SearchRepository.indexDocument], which folds the document's title,
     * notes, tags and category into the row.
     *
     * This also repairs libraries whose index was wiped by an older "rebuild" that only cleared
     * it - after this pass, searching by title, tag or note works again.
     */
    fun rebuildSearchIndex() {
        if (_busy.value != SettingsBusy.NONE) return
        _busy.value = SettingsBusy.REINDEX
        viewModelScope.launch {
            _reindexProgress.value = 0f
            try {
                val documents = documentRepository
                    .observeCollection(SmartCollection.ALL, limit = MAX_REINDEX_DOCUMENTS)
                    .first()
                runCatching { searchRepository.clearIndex() }
                documents.forEachIndexed { index, document ->
                    runCatching { searchRepository.indexDocument(document) }
                    _reindexProgress.value =
                        (index + 1).toFloat() / documents.size.coerceAtLeast(1).toFloat()
                }
                _message.value = SettingsMessage(R.string.settings_index_done, documents.size)
            } catch (throwable: Throwable) {
                DocoraLog.w(TAG, "reindex_failed", throwable)
                _message.value = SettingsMessage(R.string.settings_index_failed)
            } finally {
                _reindexProgress.value = null
                _busy.value = SettingsBusy.NONE
            }
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            runCatching { searchRepository.clearRecentSearches() }
            _message.value = SettingsMessage(R.string.settings_search_history_cleared)
        }
    }

    // ------------------------------------------------------------------ app lock

    /**
     * Turns App Lock on only after the user proves it is them.
     *
     * Turning the lock on for a stranger who found the phone first would hand them the keys, so
     * the enable path always goes through the platform prompt. Turning it off is a plain setting
     * change: without a working credential the user is locked out entirely, and there a prompt
     * would be the trap, not the guard.
     */
    fun requestAppLockEnabled() {
        if (settings.value.appLockEnabled) return
        if (!biometricAuthenticator.canAuthenticate()) {
            _message.value = SettingsMessage(R.string.settings_app_lock_unavailable)
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                appLockManager.unlock(
                    title = unlockTitle,
                    subtitle = unlockSubtitle,
                )
            }.getOrElse {
                DocoraLog.w(TAG, "app_lock_enable_prompt_failed", it)
                AuthenticationResult.Failed(it.message)
            }
            when (result) {
                is AuthenticationResult.Success -> {
                    update { it.copy(appLockEnabled = true) }
                    appLockManager.lock()
                }
                is AuthenticationResult.Cancelled -> Unit
                is AuthenticationResult.Failed -> {
                    _message.value = SettingsMessage(R.string.settings_app_lock_failed)
                }
                is AuthenticationResult.Unavailable -> {
                    _message.value = SettingsMessage(R.string.settings_app_lock_unavailable)
                }
            }
        }
    }

    fun setAppLockDisabled() = update { it.copy(appLockEnabled = false) }

    /** Locks the vault immediately; only meaningful while App Lock is on. */
    fun lockNow() {
        if (!settings.value.appLockEnabled) {
            _message.value = SettingsMessage(R.string.settings_lock_now_requires_app_lock)
            return
        }
        appLockManager.lock()
        _message.value = SettingsMessage(R.string.settings_lock_now_done)
    }

    // ------------------------------------------------------------------ backup

    fun exportBackup(uri: Uri) {
        if (_busy.value != SettingsBusy.NONE) return
        _busy.value = SettingsBusy.WORKING
        viewModelScope.launch {
            _message.value = SettingsMessage(R.string.settings_backup_running)
            when (val result = backupRepository.exportTo(uri)) {
                is DocoraResult.Success -> {
                    val summary = result.value
                    _message.value = SettingsMessage(
                        R.string.settings_backup_exported,
                        summary.folders,
                        summary.tags,
                        summary.documents,
                    )
                    DocoraLog.i(TAG, "backup_exported", "documents" to summary.documents)
                }
                is DocoraResult.Failure -> {
                    DocoraLog.w(TAG, "backup_export_failed", result.error)
                    _message.value = SettingsMessage(R.string.settings_backup_failed)
                }
            }
            _busy.value = SettingsBusy.NONE
        }
    }

    fun restoreBackup(uri: Uri) {
        if (_busy.value != SettingsBusy.NONE) return
        _busy.value = SettingsBusy.WORKING
        viewModelScope.launch {
            _message.value = SettingsMessage(R.string.settings_restore_running)
            when (val result = backupRepository.restoreFrom(uri)) {
                is DocoraResult.Success -> {
                    val summary = result.value
                    if (summary.isEmpty) {
                        _message.value = SettingsMessage(R.string.settings_restore_empty)
                    } else {
                        _message.value = SettingsMessage(
                            R.string.settings_restore_done,
                            summary.folders,
                            summary.tags,
                            summary.documents,
                        )
                    }
                    refreshStats()
                    DocoraLog.i(TAG, "backup_restored", "documents" to summary.documents)
                }
                is DocoraResult.Failure -> {
                    DocoraLog.w(TAG, "backup_restore_failed", result.error)
                    _message.value = if (result.error is DocoraError.UnsupportedFormat) {
                        SettingsMessage(R.string.settings_restore_failed)
                    } else {
                        SettingsMessage(R.string.settings_operation_failed)
                    }
                }
            }
            _busy.value = SettingsBusy.NONE
        }
    }

    /**
     * Returns every user-facing option on this screen to its default.
     *
     * The library itself is never touched, and anything that is not a setting is preserved: the
     * first-launch scan bookkeeping, the search history and the onboarding flag are context, not
     * preferences.
     */
    fun resetAllSettings() {
        viewModelScope.launch {
            val current = preferencesManager.settings.first()
            runCatching {
                preferencesManager.update {
                    AppSettings.Default.copy(
                        deviceScanAsked = current.deviceScanAsked,
                        recentSearches = current.recentSearches,
                        onboardingCompleted = current.onboardingCompleted,
                    )
                }
            }.onFailure { DocoraLog.w(TAG, "settings_reset_failed", it) }
                .onSuccess { _message.value = SettingsMessage(R.string.settings_reset_done) }
        }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            runCatching { preferencesManager.update(transform) }
                .onFailure { DocoraLog.w(TAG, "settings_update_failed", it) }
        }
    }

    /**
     * The unlock prompt is shown from *this* screen rather than the lock gate, so the exact
     * strings that wrap the system prompt are set by the screen before calling
     * [requestAppLockEnabled]. They live here, not on the AppLockManager, because they are
     * localisable UI resources and the security layer must never own copy.
     */
    var unlockTitle: String = "Unlock Docora"
    var unlockSubtitle: String = "Confirm it is you to turn App Lock on"

    private companion object {
        const val TAG = "SettingsViewModel"
        const val MAX_REINDEX_DOCUMENTS = 20_000
    }

    /** Bounds for the thumbnail-cache slider; referenced by the sheet so values cannot drift. */
    object ThumbnailCacheLimits {
        const val MIN_MB = 32
        const val STEP_MB = 32
    }
}

/** Localised snackbar payload: the UI resolves [textRes] with [args]. */
data class SettingsMessage(
    val textRes: Int,
    val args: List<Any> = emptyList(),
) {
    constructor(textRes: Int, vararg args: Any) : this(textRes, args.toList())
}

/** At most one long-running settings job runs at a time; the UI shows the right spinner. */
enum class SettingsBusy {
    NONE,
    WORKING,
    REINDEX,
}

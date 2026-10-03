package com.vedica.labs.ind.app.docora.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedica.labs.ind.app.docora.core.common.DocoraLog
import com.vedica.labs.ind.app.docora.core.datastore.PreferencesManager
import com.vedica.labs.ind.app.docora.core.files.ThumbnailStore
import com.vedica.labs.ind.app.docora.core.model.AppSettings
import com.vedica.labs.ind.app.docora.core.model.AutoLockTimeout
import com.vedica.labs.ind.app.docora.core.model.ScanFilter
import com.vedica.labs.ind.app.docora.core.model.ScanMode
import com.vedica.labs.ind.app.docora.core.model.ScannerQuality
import com.vedica.labs.ind.app.docora.core.model.SortDirection
import com.vedica.labs.ind.app.docora.core.model.SortOrder
import com.vedica.labs.ind.app.docora.core.model.ThemeMode
import com.vedica.labs.ind.app.docora.core.model.TrashRetention
import com.vedica.labs.ind.app.docora.core.model.ViewMode
import com.vedica.labs.ind.app.docora.core.repository.SearchRepository
import com.vedica.labs.ind.app.docora.core.security.BiometricAuthenticator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val searchRepository: SearchRepository,
    private val thumbnailStore: ThumbnailStore,
    private val biometricAuthenticator: BiometricAuthenticator,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = preferencesManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.Default)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    val canUseBiometrics: Boolean get() = biometricAuthenticator.canAuthenticate()
    val hasEnrolledBiometric: Boolean get() = biometricAuthenticator.hasEnrolledBiometric()

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheBytes.value = runCatching { thumbnailStore.cacheSizeBytes() }.getOrDefault(0L)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun setTheme(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setDynamicColor(enabled: Boolean) = update { it.copy(useDynamicColor = enabled) }
    fun setViewMode(mode: ViewMode) = update { it.copy(defaultViewMode = mode) }
    fun setSort(order: SortOrder, direction: SortDirection) =
        update { it.copy(defaultSortOrder = order, defaultSortDirection = direction) }

    fun setAppLock(enabled: Boolean) = update { it.copy(appLockEnabled = enabled) }
    fun setAutoLock(timeout: AutoLockTimeout) = update { it.copy(autoLockTimeout = timeout) }
    fun setSecureScreen(enabled: Boolean) = update { it.copy(secureScreenEnabled = enabled) }
    fun setEncryptText(enabled: Boolean) = update { it.copy(encryptExtractedText = enabled) }

    fun setScannerQuality(quality: ScannerQuality) = update { it.copy(scannerQuality = quality) }
    fun setScannerAutoCapture(enabled: Boolean) = update { it.copy(scannerAutoCapture = enabled) }
    fun setScannerMode(mode: ScanMode) = update { it.copy(scannerMode = mode) }
    fun setScannerFilter(filter: ScanFilter) = update { it.copy(scannerFilter = filter) }
    fun setOcrAfterScan(enabled: Boolean) = update { it.copy(ocrAfterScan = enabled) }

    fun setTrashRetention(retention: TrashRetention) = update { it.copy(trashRetention = retention) }
    fun setThumbnailCacheLimit(mb: Int) =
        update { it.copy(thumbnailCacheLimitMb = mb.coerceIn(32, AppSettings.MAX_THUMBNAIL_CACHE_MB)) }

    fun setIndexOcr(enabled: Boolean) = update { it.copy(indexOcrText = enabled) }
    fun setIndexDocuments(enabled: Boolean) = update { it.copy(indexDocumentText = enabled) }

    fun clearThumbnailCache() {
        viewModelScope.launch {
            val cleared = runCatching { thumbnailStore.clear() }.getOrDefault(0)
            _cacheBytes.value = runCatching { thumbnailStore.cacheSizeBytes() }.getOrDefault(0L)
            _message.value = "cleared:$cleared"
            DocoraLog.i(TAG, "cache_cleared", "files" to cleared)
        }
    }

    fun rebuildSearchIndex() {
        viewModelScope.launch {
            runCatching { searchRepository.clearIndex() }
            _message.value = "reindex_started"
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            runCatching { searchRepository.clearRecentSearches() }
            _message.value = "search_history_cleared"
        }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            runCatching { preferencesManager.update(transform) }
                .onFailure { DocoraLog.w(TAG, "settings_update_failed", it) }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}

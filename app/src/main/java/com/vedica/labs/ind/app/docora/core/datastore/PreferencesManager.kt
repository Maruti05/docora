package com.vedica.labs.ind.app.docora.core.datastore

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * DataStore serialization representation of user settings.
 */
@Serializable
data class AppSettingsDto(
    val themeMode: String = ThemeMode.SYSTEM.name,
    val useDynamicColor: Boolean = true,
    val reduceMotion: Boolean = false,
    val defaultViewMode: String = ViewMode.GRID.name,
    val defaultSortOrder: String = SortOrder.DATE_MODIFIED.name,
    val defaultSortDirection: String = SortDirection.DESCENDING.name,
    val appLockEnabled: Boolean = false,
    val autoLockTimeout: String = AutoLockTimeout.IMMEDIATELY.name,
    val secureScreenEnabled: Boolean = false,
    val encryptExtractedText: Boolean = false,
    val scannerQuality: String = ScannerQuality.HIGH.name,
    val scannerAutoCapture: Boolean = true,
    val scannerMode: String = ScanMode.DOCUMENT.name,
    val scannerFilter: String = ScanFilter.ENHANCE.name,
    val ocrAfterScan: Boolean = true,
    val trashRetention: String = TrashRetention.THIRTY_DAYS.name,
    val thumbnailCacheLimitMb: Int = 128,
    val indexOcrText: Boolean = true,
    val indexDocumentText: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val deviceScanAsked: Boolean = false,
    val recentSearches: List<String> = emptyList(),
)
fun AppSettingsDto.toDomain(): AppSettings = AppSettings(
    themeMode = runCatching { ThemeMode.valueOf(themeMode) }.getOrDefault(ThemeMode.SYSTEM),
    useDynamicColor = useDynamicColor,
    reduceMotion = reduceMotion,
    defaultViewMode = runCatching { ViewMode.valueOf(defaultViewMode) }.getOrDefault(ViewMode.GRID),
    defaultSortOrder = runCatching { SortOrder.valueOf(defaultSortOrder) }.getOrDefault(SortOrder.DATE_MODIFIED),
    defaultSortDirection = runCatching { SortDirection.valueOf(defaultSortDirection) }.getOrDefault(SortDirection.DESCENDING),
    appLockEnabled = appLockEnabled,
    autoLockTimeout = runCatching { AutoLockTimeout.valueOf(autoLockTimeout) }.getOrDefault(AutoLockTimeout.IMMEDIATELY),
    secureScreenEnabled = secureScreenEnabled,
    encryptExtractedText = encryptExtractedText,
    scannerQuality = runCatching { ScannerQuality.valueOf(scannerQuality) }.getOrDefault(ScannerQuality.HIGH),
    scannerAutoCapture = scannerAutoCapture,
    scannerMode = runCatching { ScanMode.valueOf(scannerMode) }.getOrDefault(ScanMode.DOCUMENT),
    scannerFilter = runCatching { ScanFilter.valueOf(scannerFilter) }.getOrDefault(ScanFilter.ENHANCE),
    ocrAfterScan = ocrAfterScan,
    trashRetention = runCatching { TrashRetention.valueOf(trashRetention) }.getOrDefault(TrashRetention.THIRTY_DAYS),
    thumbnailCacheLimitMb = thumbnailCacheLimitMb,
    indexOcrText = indexOcrText,
    indexDocumentText = indexDocumentText,
    onboardingCompleted = onboardingCompleted,
    deviceScanAsked = deviceScanAsked,
    recentSearches = recentSearches,
)

fun AppSettings.toDto(): AppSettingsDto = AppSettingsDto(
    themeMode = themeMode.name,
    useDynamicColor = useDynamicColor,
    reduceMotion = reduceMotion,
    defaultViewMode = defaultViewMode.name,
    defaultSortOrder = defaultSortOrder.name,
    defaultSortDirection = defaultSortDirection.name,
    appLockEnabled = appLockEnabled,
    autoLockTimeout = autoLockTimeout.name,
    secureScreenEnabled = secureScreenEnabled,
    encryptExtractedText = encryptExtractedText,
    scannerQuality = scannerQuality.name,
    scannerAutoCapture = scannerAutoCapture,
    scannerMode = scannerMode.name,
    scannerFilter = scannerFilter.name,
    ocrAfterScan = ocrAfterScan,
    trashRetention = trashRetention.name,
    thumbnailCacheLimitMb = thumbnailCacheLimitMb,
    indexOcrText = indexOcrText,
    indexDocumentText = indexDocumentText,
    onboardingCompleted = onboardingCompleted,
    deviceScanAsked = deviceScanAsked,
    recentSearches = recentSearches,
)

object AppSettingsSerializer : Serializer<AppSettingsDto> {
    override val defaultValue: AppSettingsDto = AppSettingsDto()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun readFrom(input: InputStream): AppSettingsDto =
        try {
            json.decodeFromString<AppSettingsDto>(input.bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            throw CorruptionException("Failed to read settings DataStore", e)
        }

    override suspend fun writeTo(t: AppSettingsDto, output: OutputStream) {
        output.bufferedWriter().use {
            it.write(json.encodeToString(t))
        }
    }
}

val Context.settingsDataStore: DataStore<AppSettingsDto> by dataStore(
    fileName = "docora_settings.json",
    serializer = AppSettingsSerializer,
)

/**
 * DataStore preferences access gateway.
 */
class PreferencesManager(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { throwable ->
            // A corrupted settings file must not break the app: it is replaced by defaults. Real
            // IO failures are rethrown so they surface instead of being silently swallowed.
            if (throwable is java.io.IOException) {
                emit(AppSettingsDto())
            } else {
                throw throwable
            }
        }
        .map { dto -> dto.toDomain() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.updateData { current ->
            transform(current.toDomain()).toDto()
        }
    }
}

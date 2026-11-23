package com.vedica.labs.ind.app.docora

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vedica.labs.ind.app.docora.core.datastore.PreferencesManager
import com.vedica.labs.ind.app.docora.core.processing.DocumentEnricher
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.security.ActivityLockHost
import com.vedica.labs.ind.app.docora.core.security.AppLockManager
import com.vedica.labs.ind.app.docora.core.security.SecureScreenController
import com.vedica.labs.ind.app.docora.core.storage.DeviceDocumentsSource
import com.vedica.labs.ind.app.docora.core.storage.DeviceLibrarySynchronizer
import com.vedica.labs.ind.app.docora.core.files.ThumbnailStore
import com.vedica.labs.ind.app.docora.ui.designsystem.DocoraTheme
import com.vedica.labs.ind.app.docora.ui.screens.DocoraApp
import com.vedica.labs.ind.app.docora.ui.screens.LockGate
import com.vedica.labs.ind.app.docora.ui.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Single-activity host (PRD §2, §32, §33).
 *
 * The activity owns exactly four concerns:
 *  1. installing the themed Compose surface,
 *  2. the App Lock lifecycle (start locked, re-lock after the configured background time),
 *  3. the FLAG_SECURE policy when the user enables the secure screen,
 *  4. the automatic device scan, so the library reflects the files already on the phone
 *     without the user having to import them one by one.
 *
 * It extends [FragmentActivity] because BiometricPrompt requires a fragment host.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val secureScreen = SecureScreenController()

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var lockHost: ActivityLockHost

    @Inject
    lateinit var deviceLibrarySynchronizer: DeviceLibrarySynchronizer

    @Inject
    lateinit var deviceDocumentsSource: DeviceDocumentsSource

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var documentEnricher: DocumentEnricher

    @Inject
    lateinit var documentRepository: DocumentRepository

    @Inject
    lateinit var thumbnailStore: ThumbnailStore

    /** Resolves the storage/media grant the automatic scan depends on. */
    private lateinit var mediaPermissionLauncher: ActivityResultLauncher<Array<String>>

    /** Returns from the system "All files access" screen on Android 11 and newer. */
    private lateinit var allFilesAccessLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        mediaPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            // Whatever the answer, the scan decides for itself: a denial simply imports nothing.
            lifecycleScope.launch { syncDeviceLibraryIfPermitted() }
        }
        allFilesAccessLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            lifecycleScope.launch { syncDeviceLibraryIfPermitted() }
        }
        super.onCreate(savedInstanceState)

        // Resume any enrichment the previous process did not finish. The pass is idempotent and
        // processes only rows still missing a checksum, text layer or OCR result.
        documentEnricher.start()
        lifecycleScope.launch { runStartupMaintenance() }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                lockHost.attach(this@MainActivity)
                lifecycleScope.launch {
                    appLockManager.onEnterForeground()
                    if (deviceDocumentsSource.hasPermission()) {
                        syncDeviceLibraryIfPermitted()
                    } else {
                        requestStorageAccessOnFirstLaunch()
                    }
                }
            }

            override fun onPause(owner: LifecycleOwner) {
                lockHost.detach(this@MainActivity)
                lifecycleScope.launch { appLockManager.onEnterBackground() }
            }
        })

        setContent {
            DocoraThemeHost(
                activity = this,
                secureScreen = secureScreen,
            )
        }
    }

    /**
     * Requests the access that makes every document on the device visible. Android 11+ gates the
     * document set behind the "All files access" system screen, so that screen is opened directly;
     * older releases use the normal runtime dialog.
     *
     * The request happens once per install. If it is declined, the documents browser keeps an
     * "Allow" card, and the automatic scan runs the moment access is granted.
     */
    private suspend fun requestStorageAccessOnFirstLaunch() {
        if (deviceDocumentsSource.hasPermission()) return
        if (preferencesManager.settings.first().deviceScanAsked) return

        preferencesManager.update { it.copy(deviceScanAsked = true) }
        if (deviceDocumentsSource.requiresAllFilesAccess()) {
            runCatching {
                allFilesAccessLauncher.launch(deviceDocumentsSource.allFilesAccessIntent())
            }
        } else {
            val permissions = deviceDocumentsSource.requiredPermissions()
            if (permissions.isNotEmpty()) {
                runCatching { mediaPermissionLauncher.launch(permissions) }
            }
        }
    }

    private suspend fun syncDeviceLibraryIfPermitted() {
        if (deviceDocumentsSource.hasPermission()) {
            deviceLibrarySynchronizer.sync()
            // Newly imported rows start unprocessed; kick the same pass so the Home progress
            // banner covers them instead of leaving them pending until the next launch.
            documentEnricher.start()
        }
    }

    /**
     * Applies the two storage policies that would otherwise be numbers in Settings with no effect:
     * documents older than the trash-retention window are purged, and the thumbnail cache is
     * trimmed to the user's ceiling. Runs once per process, off the main thread.
     */
    private suspend fun runStartupMaintenance() {
        val settings = runCatching { preferencesManager.settings.first() }.getOrNull() ?: return
        settings.trashRetention.days?.let { days ->
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
            runCatching { documentRepository.purgeTrashOlderThan(cutoff) }
        }
        withContext(Dispatchers.IO) {
            runCatching {
                thumbnailStore.trimToLimit(
                    settings.thumbnailCacheLimitMb.toLong() * 1024L * 1024L,
                )
            }
        }
    }
}

@Composable
private fun DocoraThemeHost(
    activity: MainActivity,
    secureScreen: SecureScreenController,
) {
    val vm: SettingsViewModel = hiltViewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isLocked by activity.appLockManager.isLocked.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(settings.secureScreenEnabled) {
        secureScreen.apply(activity, settings.secureScreenEnabled)
    }

    DocoraTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.useDynamicColor,
        reducedMotion = settings.reduceMotion,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (isLocked) {
                LockGate(appLockManager = activity.appLockManager)
            } else {
                DocoraApp()
            }
        }
    }
}

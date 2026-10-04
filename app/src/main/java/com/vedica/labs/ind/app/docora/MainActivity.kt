package com.vedica.labs.ind.app.docora

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
import com.vedica.labs.ind.app.docora.core.security.ActivityLockHost
import com.vedica.labs.ind.app.docora.core.security.AppLockManager
import com.vedica.labs.ind.app.docora.core.security.SecureScreenController
import com.vedica.labs.ind.app.docora.core.storage.DeviceDocumentsSource
import com.vedica.labs.ind.app.docora.core.storage.DeviceLibrarySynchronizer
import com.vedica.labs.ind.app.docora.ui.designsystem.DocoraTheme
import com.vedica.labs.ind.app.docora.ui.screens.DocoraApp
import com.vedica.labs.ind.app.docora.ui.screens.LockGate
import com.vedica.labs.ind.app.docora.ui.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
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

    /** Resolves the storage/media grant the automatic scan depends on. */
    private lateinit var mediaPermissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        mediaPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            // Whatever the answer, the scan decides for itself: a denial simply imports nothing.
            lifecycleScope.launch { deviceLibrarySynchronizer.sync() }
        }
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                lockHost.attach(this@MainActivity)
                lifecycleScope.launch { appLockManager.onEnterForeground() }
            }

            override fun onPause(owner: LifecycleOwner) {
                lockHost.detach(this@MainActivity)
                lifecycleScope.launch { appLockManager.onEnterBackground() }
            }
        })

        lifecycleScope.launch { scanDeviceOnLaunch() }

        setContent {
            DocoraThemeHost(
                activity = this,
                secureScreen = secureScreen,
            )
        }
    }

    /**
     * Imports the documents already on the device, and asks for the permission that makes it
     * possible - exactly once per install, so a user who declines is not asked again on every
     * launch. The documents browser keeps an explicit card for the cases where the prompt was
     * dismissed or the grant was later revoked.
     */
    private suspend fun scanDeviceOnLaunch() {
        if (deviceDocumentsSource.hasPermission()) {
            deviceLibrarySynchronizer.sync()
            return
        }
        if (preferencesManager.settings.first().deviceScanAsked) return

        preferencesManager.update { it.copy(deviceScanAsked = true) }
        runCatching { mediaPermissionLauncher.launch(deviceDocumentsSource.requiredPermissions()) }
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

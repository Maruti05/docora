package com.vedica.labs.ind.app.docora.core.security

import com.vedica.labs.ind.app.docora.core.common.DocoraLog
import com.vedica.labs.ind.app.docora.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the "is the vault locked" state.
 *
 * Behaviour (PRD §33):
 *  * when App Lock is enabled the app starts locked and re-locks after the configured
 *    inactivity timeout elapses while the app is in the background,
 *  * the timeout decision is made from a monotonic background timestamp, not from a
 *    timer, so it survives doze and process suspension,
 *  * unlocking always goes through [BiometricAuthenticator]; Docora never stores,
 *    compares or hashes a credential itself.
 */
class AppLockManager(
    private val authenticator: BiometricAuthenticator,
    private val settingsReader: suspend () -> com.vedica.labs.ind.app.docora.core.model.AppSettings,
    private val dispatchers: DispatcherProvider,
    scope: CoroutineScope,
) {

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private var backgroundedAtMillis: Long? = null
    private val mutex = Mutex()

    init {
        // Start locked when the feature is on, so the first frame after process start
        // never shows document content.
        scope.launch(dispatchers.default) {
            runCatching {
                val settings = settingsReader()
                if (settings.appLockEnabled) {
                    _isLocked.value = true
                    DocoraLog.d(TAG, "lock_state_initial")
                }
            }.onFailure { DocoraLog.w(TAG, "lock_init_failed", it) }
        }
    }

    /** Called when the app leaves the foreground. */
    suspend fun onEnterBackground(now: Long = System.currentTimeMillis()) {
        if (!settingsEnabled()) return
        backgroundedAtMillis = now
    }

    /**
     * Called when the app returns to the foreground: locks again once the configured
     * timeout has elapsed.
     */
    suspend fun onEnterForeground(now: Long = System.currentTimeMillis()) {
        val startedAt = backgroundedAtMillis ?: return
        val settings = settingsReader()
        backgroundedAtMillis = null
        if (!settings.appLockEnabled) {
            _isLocked.value = false
            return
        }
        val timeout = settings.autoLockTimeout.millis
        val shouldLock = when (timeout) {
            null -> false
            else -> timeout == 0L || now - startedAt >= timeout
        }
        if (shouldLock) {
            _isLocked.value = true
            DocoraLog.d(TAG, "lock_state_foreground")
        }
    }

    /** Explicit lock (used by the "lock now" action and after a settings change). */
    fun lock() {
        _isLocked.value = true
    }

    /**
     * Runs the authentication flow. Returns true when the vault should unlock.
     * Concurrent taps share one prompt instead of stacking prompts.
     */
    suspend fun unlock(
        title: String,
        subtitle: String,
    ): AuthenticationResult = mutex.withLock {
        if (!_isLocked.value) return@withLock AuthenticationResult.Success
        if (!authenticator.canAuthenticate()) {
            return@withLock AuthenticationResult.Unavailable("no-credential")
        }
        _isAuthenticating.value = true
        try {
            val result = authenticator.authenticate(title = title, subtitle = subtitle)
            if (result is AuthenticationResult.Success) {
                _isLocked.value = false
                DocoraLog.d(TAG, "unlock_succeeded")
            }
            result
        } finally {
            _isAuthenticating.value = false
        }
    }

    /** True when the device cannot authenticate at all, so the setting must stay off. */
    suspend fun canUseAppLock(): Boolean = authenticator.canAuthenticate()

    private suspend fun settingsEnabled(): Boolean = try {
        settingsReader().appLockEnabled
    } catch (throwable: Throwable) {
        DocoraLog.w(TAG, "settings_read_failed", throwable)
        false
    }

    private companion object {
        const val TAG = "AppLockManager"
    }
}

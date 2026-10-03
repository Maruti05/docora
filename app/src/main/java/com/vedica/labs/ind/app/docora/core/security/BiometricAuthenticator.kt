package com.vedica.labs.ind.app.docora.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Outcome of an authentication attempt. */
sealed interface AuthenticationResult {
    data object Success : AuthenticationResult
    data object Cancelled : AuthenticationResult
    data class Failed(val message: String?) : AuthenticationResult
    data class Unavailable(val reason: String) : AuthenticationResult
}

/**
 * Thin coroutine wrapper around [BiometricPrompt].
 *
 * Docora never stores biometric data itself: the platform owns the credential
 * (PRD §33). The prompt prefers a biometric and falls back to the device credential.
 */
class BiometricAuthenticator(
    private val activityProvider: () -> FragmentActivity?,
) {

    /** True when the device can currently authenticate with biometrics or a device PIN. */
    fun canAuthenticate(): Boolean = runCatching {
        val activity = activityProvider() ?: return false
        BiometricManager.from(activity).canAuthenticate(ALLOWED_AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    /** True specifically for biometric hardware with an enrolled credential. */
    fun hasEnrolledBiometric(): Boolean = runCatching {
        val activity = activityProvider() ?: return false
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    /**
     * Shows the prompt and suspends until it resolves. Cancelling the surrounding
     * coroutine cancels the prompt, so an activity that is destroyed mid-prompt cannot
     * leak the callback.
     */
    suspend fun authenticate(
        title: String,
        subtitle: String,
    ): AuthenticationResult {
        val activity = activityProvider()
            ?: return AuthenticationResult.Unavailable("no-host")
        if (!canAuthenticate()) {
            return AuthenticationResult.Unavailable("no-credential")
        }

        return suspendCancellableCoroutine { continuation ->
            val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(AuthenticationResult.Success)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!continuation.isActive) return
                        val result = when (errorCode) {
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_CANCELED,
                            -> AuthenticationResult.Cancelled
                            else -> AuthenticationResult.Failed(errString.toString())
                        }
                        continuation.resume(result)
                    }

                    override fun onAuthenticationFailed() {
                        // A single non-matching finger prints a hint but keeps the prompt
                        // open; only terminal errors resolve the continuation.
                    }
                },
            )
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                // The combined authenticator set lets the platform fall back to the device
                // PIN/pattern/password. When DEVICE_CREDENTIAL is allowed the platform
                // supplies its own cancel affordance, so no negative button is configured.
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                .setConfirmationRequired(false)
                .build()
            prompt.authenticate(promptInfo)
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }

    private companion object {
        /**
         * Biometric first, device credential as the documented fallback (PRD §33): Docora
         * never stores or verifies the credential itself.
         */
        const val ALLOWED_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}

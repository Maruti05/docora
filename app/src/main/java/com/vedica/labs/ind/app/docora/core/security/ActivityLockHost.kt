package com.vedica.labs.ind.app.docora.core.security

import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the [FragmentActivity] that can host a [androidx.biometric.BiometricPrompt].
 *
 * The core layer cannot depend on the UI layer, so the activity registers itself here on
 * resume and clears itself on pause; the authenticator reads it through a provider lambda.
 * This replaces the previous hardcoded `activityProvider = { null }`, which silently
 * disabled App Lock (PRD §33).
 */
class ActivityLockHost {

    private val _activity = MutableStateFlow<FragmentActivity?>(null)

    /** Current resumed activity, or null when none can host a prompt right now. */
    val activity: StateFlow<FragmentActivity?> = _activity.asStateFlow()

    fun attach(activity: FragmentActivity) {
        _activity.value = activity
    }

    fun detach(activity: FragmentActivity) {
        if (_activity.value === activity) _activity.value = null
    }
}

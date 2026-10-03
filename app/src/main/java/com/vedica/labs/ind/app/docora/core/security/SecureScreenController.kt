package com.vedica.labs.ind.app.docora.core.security

import android.app.Activity
import android.view.WindowManager

/**
 * Applies `FLAG_SECURE` to the app window when the user asks for it (PRD §32).
 *
 * Implemented as a policy object rather than calling the window API from composables so
 * that the setting has exactly one implementation and can be applied to any activity.
 */
class SecureScreenController {

    /**
     * Adds or removes `FLAG_SECURE`. Screenshots and the recent-apps thumbnail are then
     * blocked by the platform, which also covers the Compose surface.
     */
    fun apply(activity: Activity?, enabled: Boolean) {
        val window = activity?.window ?: return
        if (enabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

package com.vedica.labs.ind.app.docora.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.security.AppLockManager
import com.vedica.labs.ind.app.docora.core.security.AuthenticationResult
import com.vedica.labs.ind.app.docora.ui.designsystem.DocoraThemeTokens

/**
 * Full-screen gate shown while the vault is locked (PRD §33).
 *
 * The prompt strings are resolved in composition (they are UI resources) and passed into
 * [AppLockManager.unlock], which delegates to the platform biometric prompt; Docora never
 * sees or stores the credential. A first attempt runs automatically so the user sees the
 * prompt immediately; the button re-runs it after a cancel or failure.
 */
@Composable
fun LockGate(appLockManager: AppLockManager) {
    val scheme = MaterialTheme.colorScheme
    val motion = DocoraThemeTokens.motion
    val promptTitle = stringResource(R.string.lock_biometric_prompt_title)
    val promptSubtitle = stringResource(R.string.lock_biometric_prompt_subtitle)
    val failedLabel = stringResource(R.string.lock_auth_failed)

    var failure by remember { mutableStateOf<String?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(0) }

    val pulse = rememberInfiniteTransition(label = "lockPulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(motion.ambient), RepeatMode.Reverse),
        label = "lockPulseScale",
    )

    LaunchedEffect(attempts) {
        when (val result = appLockManager.unlock(promptTitle, promptSubtitle)) {
            // Success clears the lock flag; the host swaps this gate for the app.
            AuthenticationResult.Success -> Unit
            AuthenticationResult.Cancelled -> Unit
            is AuthenticationResult.Failed -> failure = result.message ?: failedLabel
            is AuthenticationResult.Unavailable -> unavailable = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.lock_message),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            AnimatedVisibility(
                visible = failure != null,
                enter = fadeIn(tween(motion.quick)) + scaleIn(motion.quickSpec()),
                exit = fadeOut(tween(motion.quick)) + scaleOut(motion.quickSpec()),
            ) {
                Text(
                    text = failure.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            AnimatedVisibility(visible = unavailable) {
                Text(
                    text = stringResource(R.string.lock_no_auth_available),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Button(onClick = {
                failure = null
                unavailable = false
                attempts++
            }) {
                Text(stringResource(R.string.lock_unlock))
            }
        }
    }
}

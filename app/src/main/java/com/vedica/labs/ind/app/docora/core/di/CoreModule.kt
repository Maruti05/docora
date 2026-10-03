package com.vedica.labs.ind.app.docora.core.di

import android.content.Context
import com.vedica.labs.ind.app.docora.core.datastore.PreferencesManager
import com.vedica.labs.ind.app.docora.core.security.ActivityLockHost
import com.vedica.labs.ind.app.docora.core.handler.ArchiveDocumentHandler
import com.vedica.labs.ind.app.docora.core.handler.DocumentHandler
import com.vedica.labs.ind.app.docora.core.handler.DocumentHandlerRegistry
import com.vedica.labs.ind.app.docora.core.handler.ImageDocumentHandler
import com.vedica.labs.ind.app.docora.core.handler.OfficeDocumentHandler
import com.vedica.labs.ind.app.docora.core.handler.PdfDocumentHandler
import com.vedica.labs.ind.app.docora.core.handler.TextDocumentHandler
import com.vedica.labs.ind.app.docora.core.storage.SafGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager =
        PreferencesManager(context)

    @Provides
    @Singleton
    fun provideSafGateway(@ApplicationContext context: Context): SafGateway =
        SafGateway(context)

    @Provides
    @Singleton
    fun provideDocumentHandlerRegistry(
        @ApplicationContext context: Context,
        safGateway: SafGateway,
    ): DocumentHandlerRegistry {
        val handlers: List<DocumentHandler> = listOf(
            PdfDocumentHandler(context, safGateway),
            ImageDocumentHandler(safGateway),
            TextDocumentHandler(safGateway),
            OfficeDocumentHandler(safGateway),
            ArchiveDocumentHandler(safGateway),
        )
        return DocumentHandlerRegistry(handlers)
    }

    /**
     * The host bridges the resumed activity into the core layer so the biometric prompt
     * has a fragment host. It is registered by MainActivity on resume (see ActivityLockHost).
     */
    @Provides
    @Singleton
    fun provideActivityLockHost(): ActivityLockHost = ActivityLockHost()

    @Provides
    @Singleton
    fun provideBiometricAuthenticator(
        activityLockHost: ActivityLockHost,
    ): com.vedica.labs.ind.app.docora.core.security.BiometricAuthenticator =
        com.vedica.labs.ind.app.docora.core.security.BiometricAuthenticator(
            activityProvider = { activityLockHost.activity.value },
        )

    /**
     * Process-wide App Lock state. The scope is the singleton's own: the lock must outlive
     * any screen, and the only work it does is a settings read on lock-state transitions.
     */
    @Provides
    @Singleton
    fun provideAppLockManager(
        authenticator: com.vedica.labs.ind.app.docora.core.security.BiometricAuthenticator,
        preferencesManager: PreferencesManager,
        dispatchers: com.vedica.labs.ind.app.docora.core.common.DispatcherProvider,
    ): com.vedica.labs.ind.app.docora.core.security.AppLockManager {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        return com.vedica.labs.ind.app.docora.core.security.AppLockManager(
            authenticator = authenticator,
            settingsReader = { preferencesManager.settings.first() },
            dispatchers = dispatchers,
            scope = scope,
        )
    }

    @Provides
    @Singleton
    fun provideDispatcherProvider(): com.vedica.labs.ind.app.docora.core.common.DispatcherProvider =
        com.vedica.labs.ind.app.docora.core.common.DefaultDispatchers

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

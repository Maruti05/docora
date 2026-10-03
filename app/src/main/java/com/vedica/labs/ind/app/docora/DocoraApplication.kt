package com.vedica.labs.ind.app.docora

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.crossfade
import com.vedica.labs.ind.app.docora.core.notification.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Process entry point.
 *
 * Startup cost is deliberately minimal (PRD §35): dependency injection, the notification
 * channels, the WorkManager configuration and the image loader. No database work, no
 * indexing and no file scanning happens here - document processing is always queued to
 * WorkManager so that the first frame is not delayed and nothing survives the process.
 */
@HiltAndroidApp
class DocoraApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
    }

    /**
     * WorkManager is initialised on demand (see the manifest) so that the library is not
     * started for users who never trigger background work, and so that workers can be
     * created by Hilt.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.WARN)
            .build()

    /**
     * Coil needs to decode GIF/HEIC documents and to stay strictly local: no network
     * fetcher is registered, every request resolves a `content://` URI or a cached file.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
}

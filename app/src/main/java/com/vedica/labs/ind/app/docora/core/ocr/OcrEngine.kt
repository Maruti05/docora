package com.vedica.labs.ind.app.docora.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.common.docoraRunCatching
import com.vedica.labs.ind.app.docora.core.storage.SafGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

interface OcrEngine {
    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): DocoraResult<String>
    suspend fun recognizeTextFromUri(uri: Uri): DocoraResult<String>
}

@Singleton
class MlKitOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val safGateway: SafGateway,
) : OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognizeTextFromBitmap(bitmap: Bitmap): DocoraResult<String> =
        withContext(Dispatchers.Default) {
            suspendCancellableCoroutine { continuation ->
                val image = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        if (continuation.isActive) {
                            continuation.resume(DocoraResult.Success(visionText.text))
                        }
                    }
                    .addOnFailureListener { e ->
                        if (continuation.isActive) {
                            continuation.resume(DocoraResult.Failure(DocoraError.OcrFailed(e)))
                        }
                    }
            }
        }

    override suspend fun recognizeTextFromUri(uri: Uri): DocoraResult<String> =
        withContext(Dispatchers.IO) {
            docoraRunCatching {
                val inputImage = InputImage.fromFilePath(context, uri)
                suspendCancellableCoroutine { continuation ->
                    recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            if (continuation.isActive) {
                                continuation.resume(visionText.text)
                            }
                        }
                        .addOnFailureListener { e ->
                            if (continuation.isActive) {
                                continuation.resumeWith(Result.failure(DocoraError.OcrFailed(e)))
                            }
                        }
                }
            }
        }
}


package com.vedica.labs.ind.app.docora.core.common

/**
 * Every failure that can cross a layer boundary is modelled explicitly.
 *
 * The UI maps [DocoraError] to an actionable message (PRD §41) instead of showing
 * "something went wrong", and the mapping lives in one place so that wording stays
 * consistent across features.
 */
sealed class DocoraError(
    message: String? = null,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    /** The document URI cannot be opened any more (deleted, moved, provider gone). */
    data class DocumentMissing(override val cause: Throwable? = null) : DocoraError("Document missing", cause)

    /** A persistable URI permission was revoked or was never granted. */
    data class PermissionRevoked(override val cause: Throwable? = null) : DocoraError("Permission revoked", cause)

    /** The storage provider (SD card, cloud SAF provider) is not reachable right now. */
    data class StorageUnavailable(override val cause: Throwable? = null) : DocoraError("Storage unavailable", cause)

    /** The provider rejected a write because the location is read-only. */
    data class ReadOnlyLocation(override val cause: Throwable? = null) : DocoraError("Read-only location", cause)

    /** No handler can render the file type; only "Open with" remains. */
    data class UnsupportedFormat(val mimeType: String?) : DocoraError("Unsupported format: $mimeType")

    /** PDF parsing failed: truncated, encrypted or malformed file. */
    data class CorruptedDocument(override val cause: Throwable? = null) : DocoraError("Corrupted document", cause)

    /** Text recognition failed or the model could not run. */
    data class OcrFailed(override val cause: Throwable? = null) : DocoraError("OCR failed", cause)

    /** Not enough free space for the requested export. */
    data class InsufficientStorage(override val cause: Throwable? = null) : DocoraError("Insufficient storage", cause)

    /** The user cancelled a SAF picker or dismissed a sheet. */
    data object Cancelled : DocoraError("Operation cancelled")

    /** The file exceeded the safety limits Docora enforces. */
    data class FileTooLarge(val sizeBytes: Long, val limitBytes: Long) :
        DocoraError("File too large: $sizeBytes > $limitBytes")

    /** An archive tried to expand beyond the safe limit (decompression bomb). */
    data class UnsafeArchive(val reason: String) : DocoraError("Unsafe archive: $reason")

    /** Anything unexpected: still surfaced with a cause and logged in debug builds. */
    data class Unexpected(override val cause: Throwable? = null) : DocoraError("Unexpected error", cause)
}

/**
 * Result of a fallible operation.
 *
 * A sealed result keeps call sites honest: no swallowed exceptions, no silent failures
 * (PRD §55). [map] and [onSuccess] keep usages terse.
 */
sealed class DocoraResult<out T> {
    data class Success<out T>(val value: T) : DocoraResult<T>()
    data class Failure(val error: DocoraError) : DocoraResult<Nothing>()

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value
}

inline fun <T, R> DocoraResult<T>.map(transform: (T) -> R): DocoraResult<R> = when (this) {
    is DocoraResult.Success -> DocoraResult.Success(transform(value))
    is DocoraResult.Failure -> this
}

inline fun <T> DocoraResult<T>.onSuccess(action: (T) -> Unit): DocoraResult<T> {
    if (this is DocoraResult.Success) action(value)
    return this
}

inline fun <T> DocoraResult<T>.onFailure(action: (DocoraError) -> Unit): DocoraResult<T> {
    if (this is DocoraResult.Failure) action(error)
    return this
}

/** Wraps a suspending block, translating exceptions into [DocoraError]s. */
inline fun <T> docoraRunCatching(
    errorFactory: (Throwable) -> DocoraError = { DocoraError.Unexpected(it) },
    block: () -> T,
): DocoraResult<T> = try {
    DocoraResult.Success(block())
} catch (cancellation: kotlinx.coroutines.CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    DocoraResult.Failure(errorFactory(throwable))
}

package com.vedica.labs.ind.app.docora.core.util

import java.io.InputStream
import java.security.MessageDigest

/**
 * Streaming SHA-256 used for duplicate detection (PRD §20) and for verifying that a
 * file has not changed since it was indexed.
 */
object Hashing {

    private const val BUFFER_SIZE = 64 * 1024

    /**
     * Hashes [input] without loading it into memory, reporting progress through
     * [onBytesRead] so the UI can show a determinate progress bar for huge files.
     */
    fun sha256(
        input: InputStream,
        onBytesRead: ((Long) -> Unit)? = null,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
            total += read
            onBytesRead?.invoke(total)
        }
        return digest.digest().toHexString()
    }

    /** Convenience for already in-memory payloads (backup manifests, small test inputs). */
    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()

    fun toHexString(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            if (value < 0x10) builder.append('0')
            builder.append(Integer.toHexString(value))
        }
        return builder.toString()
    }

    /**
     * Groups documents by a cheap pre-filter: files with different sizes can never be
     * identical, so hashing only runs on size collisions.
     */
    fun fingerprint(sizeBytes: Long, firstChunk: ByteArray): String =
        sha256(buildString {
            append(sizeBytes)
            append(':')
        }.toByteArray() + firstChunk)
}

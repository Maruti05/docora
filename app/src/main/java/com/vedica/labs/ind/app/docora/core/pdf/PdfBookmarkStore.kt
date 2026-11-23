package com.vedica.labs.ind.app.docora.core.pdf

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bookmark pages per document (PRD A�10).
 *
 * Bookmarks are a reading aid, not document content, so they live in the app's own data
 * store instead of being injected into the user's PDF. Keeping them out of the PDF also
 * means a bookmark can never make the original unavailable when its provider is read-only.
 */
@Serializable
data class PdfBookmarksDto(
    val documents: Map<String, List<Int>> = emptyMap(),
)

object PdfBookmarksSerializer : Serializer<PdfBookmarksDto> {
    override val defaultValue: PdfBookmarksDto = PdfBookmarksDto()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun readFrom(input: InputStream): PdfBookmarksDto = try {
        json.decodeFromString(input.bufferedReader().use { it.readText() })
    } catch (error: Exception) {
        throw CorruptionException("Failed to read PDF bookmarks", error)
    }

    override suspend fun writeTo(t: PdfBookmarksDto, output: OutputStream) {
        output.bufferedWriter().use { it.write(json.encodeToString(t)) }
    }
}

private val Context.pdfBookmarksDataStore: DataStore<PdfBookmarksDto> by dataStore(
    fileName = "docora_pdf_bookmarks.json",
    serializer = PdfBookmarksSerializer,
)

@Singleton
class PdfBookmarkStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private val store get() = context.pdfBookmarksDataStore

    fun observe(documentId: String): Flow<Set<Int>> =
        store.data
            .catch { error ->
                if (error is java.io.IOException) emit(PdfBookmarksDto()) else throw error
            }
            .map { dto -> dto.documents[documentId]?.toSortedSet() ?: emptySet() }

    /** Flips the bookmark and returns whether the page is now bookmarked. */
    suspend fun toggle(documentId: String, pageIndex: Int): Boolean {
        var bookmarked = false
        store.updateData { current ->
            val pages = current.documents[documentId].orEmpty().toMutableSet()
            bookmarked = if (pages.contains(pageIndex)) {
                pages.remove(pageIndex)
                false
            } else {
                pages.add(pageIndex)
                true
            }
            current.copy(documents = current.documents + (documentId to pages.sorted()))
        }
        return bookmarked
    }

    suspend fun setBookmarked(documentId: String, pageIndex: Int, bookmarked: Boolean) {
        store.updateData { current ->
            val pages = current.documents[documentId].orEmpty().toMutableSet()
            if (bookmarked) pages.add(pageIndex) else pages.remove(pageIndex)
            current.copy(documents = current.documents + (documentId to pages.sorted()))
        }
    }
}

package com.vedica.labs.ind.app.docora.core.database.converter

import androidx.room.TypeConverter
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.core.model.DocumentSource
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.model.OcrStatus
import com.vedica.labs.ind.app.docora.core.model.TextIndexStatus
import com.vedica.labs.ind.app.docora.core.model.TextOrigin

/**
 * Room type converters for the domain enums.
 *
 * Enums are persisted by `name` (not ordinal) so that reordering an enum, or inserting a
 * new constant in the middle, can never silently reinterpret stored rows. Unknown values
 * fall back to a safe default instead of throwing while a row is being read.
 */
class RoomConverters {

    @TypeConverter
    fun fromDocumentType(type: DocumentType?): String? = type?.name

    @TypeConverter
    fun toDocumentType(name: String?): DocumentType? = when (name) {
        null -> null
        else -> runCatching { DocumentType.valueOf(name) }.getOrDefault(DocumentType.UNKNOWN)
    }

    @TypeConverter
    fun fromDocumentSource(source: DocumentSource?): String? = source?.name

    @TypeConverter
    fun toDocumentSource(name: String?): DocumentSource? = when (name) {
        null -> null
        else -> runCatching { DocumentSource.valueOf(name) }.getOrDefault(DocumentSource.IMPORTED)
    }

    @TypeConverter
    fun fromTextIndexStatus(status: TextIndexStatus?): String? = status?.name

    @TypeConverter
    fun toTextIndexStatus(name: String?): TextIndexStatus? = when (name) {
        null -> null
        else -> runCatching { TextIndexStatus.valueOf(name) }
            .getOrDefault(TextIndexStatus.NOT_PROCESSED)
    }

    @TypeConverter
    fun fromOcrStatus(status: OcrStatus?): String? = status?.name

    @TypeConverter
    fun toOcrStatus(name: String?): OcrStatus? = when (name) {
        null -> null
        else -> runCatching { OcrStatus.valueOf(name) }.getOrDefault(OcrStatus.NOT_PROCESSED)
    }

    @TypeConverter
    fun fromTextOrigin(origin: TextOrigin?): String? = origin?.name

    @TypeConverter
    fun toTextOrigin(name: String?): TextOrigin? = when (name) {
        null -> null
        else -> runCatching { TextOrigin.valueOf(name) }.getOrDefault(TextOrigin.NONE)
    }

    @TypeConverter
    fun fromDocumentCategory(category: DocumentCategory?): String? = category?.name

    @TypeConverter
    fun toDocumentCategory(name: String?): DocumentCategory? = when (name) {
        null -> null
        else -> DocumentCategory.fromName(name)
    }
}


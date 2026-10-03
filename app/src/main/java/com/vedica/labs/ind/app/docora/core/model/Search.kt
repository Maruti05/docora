package com.vedica.labs.ind.app.docora.core.model

/** Filters that can be combined with a search query (PRD §8, §25). */
data class SearchFilters(
    val types: Set<DocumentType> = emptySet(),
    val tagIds: Set<String> = emptySet(),
    val folderIds: Set<String> = emptySet(),
    val dateRange: DateRangeFilter = DateRangeFilter.ANY,
    val favoritesOnly: Boolean = false,
    val includeTrashed: Boolean = false,
) {
    val isEmpty: Boolean
        get() = types.isEmpty() &&
            tagIds.isEmpty() &&
            folderIds.isEmpty() &&
            dateRange == DateRangeFilter.ANY &&
            !favoritesOnly &&
            !includeTrashed

    val activeCount: Int
        get() = (if (types.isEmpty()) 0 else 1) +
            (if (tagIds.isEmpty()) 0 else 1) +
            (if (folderIds.isEmpty()) 0 else 1) +
            (if (dateRange == DateRangeFilter.ANY) 0 else 1) +
            (if (favoritesOnly) 1 else 0) +
            (if (includeTrashed) 1 else 0)

    companion object {
        val None = SearchFilters()
    }
}

/** Coarse date buckets: precise pickers add platform complexity without helping search. */
enum class DateRangeFilter(val days: Int?) {
    ANY(null),
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
    THIS_YEAR(null),
    ;

    /** Lower bound in epoch millis for [now], or null when unbounded. */
    fun lowerBoundMillis(now: Long): Long? = when (this) {
        ANY -> null
        LAST_7_DAYS -> now - 7L * 24 * 60 * 60 * 1000
        LAST_30_DAYS -> now - 30L * 24 * 60 * 60 * 1000
        THIS_YEAR -> {
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = now
            calendar.set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY)
            calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        }
    }
}

/** A single full-text search hit: the document id plus the matched context. */
data class SearchHit(
    val documentId: String,
    val rank: Double,
    val snippet: String,
    val matchedFields: List<String>,
)

/** A search result hydrated with its document. */
data class SearchResultItem(
    val document: Document,
    val snippet: String,
    val rank: Double,
)

/** How many documents still have to be indexed, shown while searching. */
data class IndexingProgress(
    val total: Int,
    val processed: Int,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else processed.toFloat() / total.toFloat()
    val isComplete: Boolean
        get() = total > 0 && processed >= total
}

package com.vedica.labs.ind.app.docora.core.database.query

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.vedica.labs.ind.app.docora.core.model.DocumentFilter
import com.vedica.labs.ind.app.docora.core.model.DocumentSort
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.model.SmartCollection
import com.vedica.labs.ind.app.docora.core.model.SortDirection
import com.vedica.labs.ind.app.docora.core.model.SortOrder

/**
 * Builds the sorted, paged document queries used by the browser.
 *
 * Why raw SQL at all: Room needs a compile-time constant statement per sort order, which would
 * mean 6 sort fields x 2 directions x N collections as separate DAO methods. Instead the ORDER
 * BY expression is selected from a **whitelist** that mirrors the table's indexes, and the only
 * values that ever come from user input (folder id, filters, page offset) are passed as bound
 * arguments. Nothing user supplied is concatenated into the statement text, so this cannot
 * become an injection point.
 *
 * The builder is a pure function of its inputs, which makes the generated SQL unit testable.
 */
object DocumentQueryBuilder {

    /** Every column combination the browser can ask for, mapped to an indexed expression. */
    private fun orderByClause(sort: DocumentSort): String {
        val direction = if (sort.direction == SortDirection.ASCENDING) "ASC" else "DESC"
        val expression = when (sort.order) {
            SortOrder.NAME -> "display_name COLLATE NOCASE"
            SortOrder.DATE_MODIFIED -> "modified_at"
            SortOrder.DATE_CREATED -> "created_at"
            SortOrder.SIZE -> "size_bytes"
            SortOrder.TYPE -> "document_type"
            SortOrder.LAST_OPENED -> "COALESCE(last_opened_at, 0)"
        }
        // Pinned documents always float to the top; that is a product rule, not a preference.
        return "is_pinned DESC, $expression $direction, display_name COLLATE NOCASE ASC"
    }

    /**
     * Collection + optional filter + sort + page, resolved entirely by SQLite.
     *
     * [offset] drives incremental paging: the browser asks for the next window instead of
     * loading the whole library into memory (PRD §35).
     */
    fun collectionQuery(
        collection: SmartCollection,
        filter: DocumentFilter,
        sort: DocumentSort,
        limit: Int,
        offset: Int = 0,
        searchQuery: String? = null,
    ): SupportSQLiteQuery {
        val builder = WhereBuilder()
        builder.addCollection(collection)
        builder.addFilter(filter)
        searchQuery?.takeIf { it.isNotBlank() }?.let { builder.addNameMatch(it) }
        return builder.toSortedQuery(orderByClause(sort), limit, offset)
    }

    /** Contents of one folder (null = virtual root), sorted and paged. */
    fun folderQuery(
        folderId: String?,
        sort: DocumentSort,
        limit: Int,
        offset: Int = 0,
    ): SupportSQLiteQuery {
        val builder = WhereBuilder()
        builder.add("is_trash = 0 AND is_archived = 0")
        if (folderId == null) {
            builder.add("folder_id IS NULL")
        } else {
            builder.add("folder_id = ?", folderId)
        }
        return builder.toSortedQuery(orderByClause(sort), limit, offset)
    }

    /** Ids only: used by "select all", which must not hydrate thousands of rows. */
    fun collectionIdsQuery(collection: SmartCollection, filter: DocumentFilter): SupportSQLiteQuery {
        val builder = WhereBuilder()
        builder.addCollection(collection)
        builder.addFilter(filter)
        val sql = "SELECT id FROM documents WHERE ${builder.sql} ORDER BY modified_at DESC"
        return SimpleSQLiteQuery(sql, builder.args.toTypedArray())
    }

    private class WhereBuilder {
        val args = mutableListOf<Any?>()
        private val clauses = mutableListOf<String>()

        val sql: String get() = if (clauses.isEmpty()) "1" else clauses.joinToString(" AND ")

        fun add(clause: String) {
            clauses += clause
        }

        fun add(clause: String, vararg values: Any?) {
            clauses += clause
            args.addAll(values)
        }

        fun toSortedQuery(orderBy: String, limit: Int, offset: Int): SupportSQLiteQuery =
            SimpleSQLiteQuery(
                "SELECT * FROM documents WHERE $sql ORDER BY $orderBy LIMIT $limit OFFSET $offset",
                args.toTypedArray(),
            )

        fun addCollection(collection: SmartCollection) {
            when (collection) {
                SmartCollection.ALL -> add("is_trash = 0 AND is_archived = 0")
                SmartCollection.RECENT -> add("is_trash = 0 AND last_opened_at IS NOT NULL")
                SmartCollection.FAVORITES -> add("is_trash = 0 AND is_favorite = 1")
                SmartCollection.SCANS -> add("is_trash = 0 AND is_archived = 0 AND source = 'SCANNED'")
                SmartCollection.PDFS -> add("is_trash = 0 AND is_archived = 0 AND document_type = 'PDF'")
                SmartCollection.IMAGES -> add("is_trash = 0 AND is_archived = 0 AND document_type = 'IMAGE'")
                SmartCollection.LARGE_FILES -> add("is_trash = 0 AND is_archived = 0")
                SmartCollection.RECENTLY_MODIFIED -> add("is_trash = 0 AND is_archived = 0")
                SmartCollection.TRASH -> add("is_trash = 1")
                SmartCollection.DUPLICATES -> add(DUPLICATE_CLAUSE)
            }
        }

        fun addFilter(filter: DocumentFilter) {
            when (filter) {
                DocumentFilter.ALL -> Unit
                DocumentFilter.PDF -> add("document_type = ?", DocumentType.PDF.name)
                DocumentFilter.IMAGES -> add("document_type = ?", DocumentType.IMAGE.name)
                DocumentFilter.DOCUMENTS -> add(
                    "document_type IN (?, ?, ?)",
                    DocumentType.WORD_DOCUMENT.name,
                    DocumentType.TEXT.name,
                    DocumentType.SPREADSHEET.name,
                )
                DocumentFilter.ARCHIVES -> add("document_type = ?", DocumentType.ARCHIVE.name)
                DocumentFilter.FAVORITES -> add("is_favorite = 1")
                DocumentFilter.NEEDS_OCR -> add(
                    "document_type IN (?, ?) AND ocr_state IN ('NOT_PROCESSED', 'PROCESSING')",
                    DocumentType.PDF.name,
                    DocumentType.IMAGE.name,
                )
            }
        }

        fun addNameMatch(query: String) {
            add("display_name LIKE ? COLLATE NOCASE", "%${query.trim()}%")
        }
    }

    private const val DUPLICATE_CLAUSE =
        "is_trash = 0 AND checksum_sha256 IN (" +
            "SELECT checksum_sha256 FROM documents WHERE is_trash = 0 " +
            "AND checksum_sha256 IS NOT NULL GROUP BY checksum_sha256 HAVING COUNT(*) > 1)"
}

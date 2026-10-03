package com.vedica.labs.ind.app.docora.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Full-text search index (PRD §8).
 *
 * Design notes:
 *
 *  * The table is a **standalone FTS4** virtual table rather than a `contentEntity`-backed
 *    one. Room does not keep an external-content FTS table in sync on its own, and a silent
 *    index/content drift is exactly the class of bug that makes search feel broken, so the
 *    index rows are written explicitly by [com.vedica.labs.ind.app.docora.core.repository.SearchRepository]
 *    inside the same transaction as the document write.
 *  * FTS4 (not FTS5) is used deliberately: FTS5 is not present in the SQLite build shipped
 *    by every supported Android version, while FTS3/FTS4 is guaranteed from API 26 upwards.
 *    FTS4 already provides everything Docora needs - prefix indexes, MATCH, and per-column
 *    matching - and Room has first class support for it (see `docs/DESIGN.md`).
 *  * `prefix = ["2", "3"]` keeps prefix queries (typing "ins" for "insurance") fast without a
 *    full scan, which is what makes instant search feel instant on large libraries.
 *  * [docId] duplicates `documents.id` and is `notIndexed`: it is a join key, never a search
 *    token, so it must not pollute match results.
 */
@Fts4(notIndexed = ["doc_id"], prefix = [2, 3])
@Entity(tableName = "documents_fts")
data class DocumentFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowid: Long = 0,
    @ColumnInfo(name = "doc_id")
    val docId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "body")
    val body: String,
    @ColumnInfo(name = "notes")
    val notes: String,
    @ColumnInfo(name = "tags")
    val tags: String,
    @ColumnInfo(name = "category")
    val category: String,
)


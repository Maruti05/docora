package com.vedica.labs.ind.app.docora.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vedica.labs.ind.app.docora.core.database.converter.RoomConverters
import com.vedica.labs.ind.app.docora.core.database.dao.DocumentDao
import com.vedica.labs.ind.app.docora.core.database.dao.DocumentPageDao
import com.vedica.labs.ind.app.docora.core.database.dao.FolderDao
import com.vedica.labs.ind.app.docora.core.database.dao.SearchDao
import com.vedica.labs.ind.app.docora.core.database.dao.TagDao
import com.vedica.labs.ind.app.docora.core.database.entity.DocumentEntity
import com.vedica.labs.ind.app.docora.core.database.entity.DocumentFtsEntity
import com.vedica.labs.ind.app.docora.core.database.entity.DocumentPageEntity
import com.vedica.labs.ind.app.docora.core.database.entity.DocumentTagCrossRef
import com.vedica.labs.ind.app.docora.core.database.entity.FolderEntity
import com.vedica.labs.ind.app.docora.core.database.entity.TagEntity

/**
 * Main application Room database.
 *
 * Export schema is enabled (to `app/schemas`) so future migrations are verified against
 * the shipped schema (PRD §44).
 */
@Database(
    entities = [
        DocumentEntity::class,
        DocumentFtsEntity::class,
        FolderEntity::class,
        TagEntity::class,
        DocumentTagCrossRef::class,
        DocumentPageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class DocoraDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao
    abstract fun documentPageDao(): DocumentPageDao
    abstract fun searchDao(): SearchDao

    companion object {
        const val DATABASE_NAME = "docora.db"
    }
}

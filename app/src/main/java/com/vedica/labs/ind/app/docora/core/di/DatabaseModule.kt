package com.vedica.labs.ind.app.docora.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.vedica.labs.ind.app.docora.core.database.DocoraDatabase
import com.vedica.labs.ind.app.docora.core.database.dao.DocumentDao
import com.vedica.labs.ind.app.docora.core.database.dao.DocumentPageDao
import com.vedica.labs.ind.app.docora.core.database.dao.FolderDao
import com.vedica.labs.ind.app.docora.core.database.dao.SearchDao
import com.vedica.labs.ind.app.docora.core.database.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DocoraDatabase =
        Room.databaseBuilder(
            context,
            DocoraDatabase::class.java,
            DocoraDatabase.DATABASE_NAME,
        )
            // Docora ships version 1 of the schema. Until a release has been published there is
            // nothing to migrate *from*, and a destructive fallback keeps a development build from
            // getting stuck on an incompatible local database. Every published version bump must
            // instead add a real migration to `DocoraMigrations` and drop this call.
            .fallbackToDestructiveMigration(dropAllTables = true)
            // Foreign keys are declared on document_pages, so they must be enforced.
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides
    fun provideDocumentDao(database: DocoraDatabase): DocumentDao = database.documentDao()

    @Provides
    fun provideFolderDao(database: DocoraDatabase): FolderDao = database.folderDao()

    @Provides
    fun provideTagDao(database: DocoraDatabase): TagDao = database.tagDao()

    @Provides
    fun provideDocumentPageDao(database: DocoraDatabase): DocumentPageDao = database.documentPageDao()

    @Provides
    fun provideSearchDao(database: DocoraDatabase): SearchDao = database.searchDao()
}

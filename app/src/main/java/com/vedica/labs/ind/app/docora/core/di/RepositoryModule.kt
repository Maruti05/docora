package com.vedica.labs.ind.app.docora.core.di

import com.vedica.labs.ind.app.docora.core.backup.BackupRepository
import com.vedica.labs.ind.app.docora.core.backup.BackupRepositoryImpl
import com.vedica.labs.ind.app.docora.core.ocr.MlKitOcrEngine
import com.vedica.labs.ind.app.docora.core.ocr.OcrEngine
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepositoryImpl
import com.vedica.labs.ind.app.docora.core.repository.FolderRepository
import com.vedica.labs.ind.app.docora.core.repository.FolderRepositoryImpl
import com.vedica.labs.ind.app.docora.core.repository.SearchRepository
import com.vedica.labs.ind.app.docora.core.repository.SearchRepositoryImpl
import com.vedica.labs.ind.app.docora.core.repository.TagRepository
import com.vedica.labs.ind.app.docora.core.repository.TagRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds every repository interface to its implementation.
 *
 * The domain and UI layers depend only on the interfaces, so a test can substitute a fake and a
 * future remote (sync, backup) source can be added without touching a single call site.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindOcrEngine(impl: MlKitOcrEngine): OcrEngine
}


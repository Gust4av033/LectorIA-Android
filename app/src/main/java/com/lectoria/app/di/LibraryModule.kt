package com.lectoria.app.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.lectoria.app.data.LibraryPreferences
import com.lectoria.app.data.RoomBookRepository
import com.lectoria.app.data.importing.LibraryFiles
import com.lectoria.app.data.importing.SafSourceReader
import com.lectoria.app.data.local.LibraryDatabase
import com.lectoria.app.domain.repository.BookRepository
import com.lectoria.app.domain.repository.LibraryPreferencesRepository
import com.lectoria.app.domain.usecase.DeleteBook
import com.lectoria.app.domain.usecase.DiscardFailedImport
import com.lectoria.app.domain.usecase.ImportBook
import com.lectoria.app.domain.usecase.ObserveLibrary
import com.lectoria.app.domain.usecase.ReconcileLibrary
import com.lectoria.app.domain.usecase.SetLibraryOrder
import com.lectoria.app.engine.document.AndroidDocumentInspector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): LibraryDatabase =
        Room.databaseBuilder(context, LibraryDatabase::class.java, "library.db").build()

    @Provides @Singleton
    fun books(@ApplicationContext context: Context, database: LibraryDatabase): BookRepository = RoomBookRepository(
        database, LibraryFiles(File(context.filesDir, "library")), SafSourceReader(context.contentResolver), AndroidDocumentInspector(),
    )

    @Provides @Singleton
    fun preferences(@ApplicationContext context: Context): LibraryPreferencesRepository = LibraryPreferences(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("library_settings") },
    )

    @Provides fun observeLibrary(books: BookRepository, preferences: LibraryPreferencesRepository) = ObserveLibrary(books, preferences)
    @Provides fun importBook(books: BookRepository) = ImportBook(books)
    @Provides fun deleteBook(books: BookRepository) = DeleteBook(books)
    @Provides fun reconcileLibrary(books: BookRepository) = ReconcileLibrary(books)
    @Provides fun discardFailedImport(books: BookRepository) = DiscardFailedImport(books)
    @Provides fun setLibraryOrder(preferences: LibraryPreferencesRepository) = SetLibraryOrder(preferences)
}

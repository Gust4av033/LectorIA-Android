package com.lectoria.app.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.lectoria.app.data.RoomNarrationProgressRepository
import com.lectoria.app.data.SpeechPreferences
import com.lectoria.app.data.local.LibraryDatabase
import com.lectoria.app.domain.document.DocumentReader
import com.lectoria.app.domain.narration.LocalNarrationEngine
import com.lectoria.app.domain.narration.NarrationCoordinator
import com.lectoria.app.domain.narration.NarrationProgressRepository
import com.lectoria.app.domain.narration.SpeechAudioFocus
import com.lectoria.app.domain.narration.SpeechPreferencesRepository
import com.lectoria.app.domain.repository.BookRepository
import com.lectoria.app.domain.repository.ReadingProgressRepository
import com.lectoria.app.engine.narration.AndroidLocalNarrationEngine
import com.lectoria.app.engine.playback.AndroidSpeechAudioFocus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier @Retention(AnnotationRetention.BINARY)
annotation class NarrationScope

@Module @InstallIn(SingletonComponent::class)
object NarrationModule {
    @Provides @Singleton
    fun client(@ApplicationContext context: Context, coordinator: NarrationCoordinator): com.lectoria.app.domain.narration.NarrationClient =
        com.lectoria.app.engine.playback.AndroidNarrationClient(context, coordinator)
    @Provides @Singleton @NarrationScope
    fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Provides @Singleton
    fun engine(@ApplicationContext context: Context): LocalNarrationEngine = AndroidLocalNarrationEngine(context)
    @Provides @Singleton
    fun focus(@ApplicationContext context: Context): SpeechAudioFocus = AndroidSpeechAudioFocus(context)
    @Provides @Singleton
    fun bookmarks(database: LibraryDatabase): NarrationProgressRepository = RoomNarrationProgressRepository(database)
    @Provides @Singleton
    fun preferences(@ApplicationContext context: Context): SpeechPreferencesRepository = SpeechPreferences(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("speech_settings") },
    )
    @Provides @Singleton
    fun coordinator(books: BookRepository, reader: DocumentReader, reading: ReadingProgressRepository,
        bookmarks: NarrationProgressRepository, preferences: SpeechPreferencesRepository, engine: LocalNarrationEngine,
        focus: SpeechAudioFocus, @NarrationScope scope: CoroutineScope) =
        NarrationCoordinator(books, reader, reading, bookmarks, preferences, engine, focus, scope)
}

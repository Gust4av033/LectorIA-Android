package com.lectoria.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lectoria.app.domain.model.LibraryOrder
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LibraryPreferencesTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `selected order survives closing and reopening DataStore`() = runBlocking {
        val file = File(temp.root, "settings.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = LibraryPreferences(PreferenceDataStoreFactory.create(scope = firstScope) { file })
        assertEquals(LibraryOrder.RECENT, first.order.first())
        first.setOrder(LibraryOrder.TITLE)
        firstScope.coroutineContext[Job]!!.cancelAndJoin()
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val second = LibraryPreferences(PreferenceDataStoreFactory.create(scope = secondScope) { file })
            assertEquals(LibraryOrder.TITLE, second.order.first())
        } finally { secondScope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}

package io.github.gativrek.momenta.data.playlists

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The ordering transactions against a real (in-memory) SQLite database. */
@RunWith(AndroidJUnit4::class)
class PlaylistDaoTest {
    private lateinit var db: MomentaDatabase
    private lateinit var dao: PlaylistDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MomentaDatabase::class.java).build()
        dao = db.playlistDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entry(name: String) =
        PlaylistEntryEntity(
            playlistId = 0,
            position = 0,
            mediaStoreId = name.hashCode().toLong(),
            relativePath = "Music/",
            displayName = name,
            durationMs = 1_000,
            sizeBytes = 1_000,
            title = name,
            artist = null,
        )

    private suspend fun names(playlistId: Long) = dao.entries(playlistId).map { it.displayName }

    private suspend fun newPlaylist(vararg songs: String): Long {
        val id = dao.insertPlaylist(PlaylistEntity(name = "Test", createdAt = 0, updatedAt = 0))
        dao.append(id, songs.map(::entry), now = 1)
        return id
    }

    @Test
    fun appendKeepsOrderAndAllowsDuplicates() =
        runBlocking {
            val id = newPlaylist("a", "b")
            dao.append(id, listOf(entry("a")), now = 2)
            assertEquals(listOf("a", "b", "a"), names(id))
            assertEquals(listOf(0, 1, 2), dao.entries(id).map { it.position })
        }

    @Test
    fun moveRenumbersWithoutGaps() =
        runBlocking {
            val id = newPlaylist("a", "b", "c", "d")
            dao.move(id, from = 0, to = 2, now = 2)
            assertEquals(listOf("b", "c", "a", "d"), names(id))
            assertEquals(listOf(0, 1, 2, 3), dao.entries(id).map { it.position })
        }

    @Test
    fun removeClosesTheGap() =
        runBlocking {
            val id = newPlaylist("a", "b", "c")
            val b = dao.entries(id)[1]
            dao.remove(id, b.id, now = 2)
            assertEquals(listOf("a", "c"), names(id))
            assertEquals(listOf(0, 1), dao.entries(id).map { it.position })
        }

    @Test
    fun deletingAPlaylistDeletesItsEntries() =
        runBlocking {
            val id = newPlaylist("a", "b")
            dao.deletePlaylist(id)
            assertTrue(dao.entries(id).isEmpty())
            assertTrue(dao.observeSummaries().first().isEmpty())
        }

    @Test
    fun summaryCountsEntriesAndPicksTheFirstForTheCover() =
        runBlocking {
            val id = newPlaylist("first", "second")
            val summary = dao.observeSummaries().first().single()
            assertEquals(id, summary.id)
            assertEquals(2, summary.entryCount)
            assertEquals("first".hashCode().toLong(), summary.firstMediaStoreId)
        }
}

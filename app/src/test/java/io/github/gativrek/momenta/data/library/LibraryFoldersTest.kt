package io.github.gativrek.momenta.data.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFoldersTest {
    @Test
    fun `messaging apps, recorders and system sounds are left out by default`() {
        assertTrue(LibraryFolders.hiddenByDefault("WhatsApp/Media/WhatsApp Audio/"))
        assertTrue(LibraryFolders.hiddenByDefault("Android/media/org.telegram.messenger/Telegram/Telegram Audio/"))
        assertTrue(LibraryFolders.hiddenByDefault("Recordings/Voice Recorder/"))
        assertTrue(LibraryFolders.hiddenByDefault("Notifications/"))
        assertTrue(LibraryFolders.hiddenByDefault("Music/Ringtones/"))
    }

    @Test
    fun `music folders are in`() {
        assertFalse(LibraryFolders.hiddenByDefault("Music/Momenta Excitantia et Aura/"))
        assertFalse(LibraryFolders.hiddenByDefault("Download/"))
        assertFalse(LibraryFolders.hiddenByDefault("Music/Callisto Protocol OST/"))
    }

    @Test
    fun `the user's choice wins over the default`() {
        val choices =
            FolderChoices(mapOf("WhatsApp/Media/WhatsApp Audio/" to true, "Music/Demos/" to false))
        assertTrue(choices.isIncluded("WhatsApp/Media/WhatsApp Audio/"))
        assertFalse(choices.isIncluded("Music/Demos/"))
        assertFalse(choices.isIncluded("WhatsApp/Media/WhatsApp Voice Notes/"))
    }
}

package io.github.gativrek.momenta.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun `bottom bar has the five library tabs in order`() {
        assertEquals(
            listOf("songs", "albums", "artists", "folders", "playlists"),
            TopLevelDestination.entries.map { it.route },
        )
    }

    @Test
    fun `routes are unique`() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }
}

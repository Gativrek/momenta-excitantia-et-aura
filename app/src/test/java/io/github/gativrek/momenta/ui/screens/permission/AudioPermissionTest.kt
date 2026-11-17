package io.github.gativrek.momenta.ui.screens.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPermissionTest {
    @Test
    fun `granted wins regardless of rationale`() {
        assertEquals(PermissionStatus.Granted, statusAfterRequest(granted = true, shouldShowRationale = false))
    }

    @Test
    fun `first denial can still ask again`() {
        assertEquals(PermissionStatus.Requestable, statusAfterRequest(granted = false, shouldShowRationale = true))
    }

    @Test
    fun `denial without rationale is permanent`() {
        assertEquals(
            PermissionStatus.PermanentlyDenied,
            statusAfterRequest(granted = false, shouldShowRationale = false),
        )
    }
}

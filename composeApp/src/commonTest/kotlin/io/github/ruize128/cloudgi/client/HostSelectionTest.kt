package io.github.ruize128.cloudgi.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HostSelectionTest {
    @Test fun previousPdHostDoesNotBlockTheOnlyMiniPc() {
        assertEquals("minipc", chooseHostId(listOf("minipc"), "previous-pd-host"))
    }
    @Test fun changingToAnnexUsesTheOnlyAvailableIdentity() {
        assertEquals("minipc-annex", chooseHostId(listOf("minipc-annex"), "minipc-main"))
    }
    @Test fun keepsAnAvailableUserSelectionAmongMultipleHosts() {
        assertEquals("minipc", chooseHostId(listOf("pd", "minipc"), "minipc"))
    }
    @Test fun multipleNewHostsRequireSelection() {
        assertNull(chooseHostId(listOf("pd", "minipc"), "old-host"))
    }
    @Test fun duplicateDiscoverySourcesDoNotCreateAmbiguity() {
        assertEquals("minipc", chooseHostId(listOf("minipc", "minipc"), null))
        assertNull(chooseHostId(emptyList(), "old-host"))
    }
}

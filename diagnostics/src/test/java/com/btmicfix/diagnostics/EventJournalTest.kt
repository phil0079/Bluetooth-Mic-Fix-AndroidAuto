package com.btmicfix.diagnostics

import org.junit.Assert.*
import org.junit.Test

class EventJournalTest {
    @Test fun aliasesDoNotExposeIdentifiersAndResetBetweenSessions() {
        val journal = EventJournal()
        val address = "AA:BB:CC:DD:EE:FF"
        val alias = journal.alias(address)
        assertEquals(alias, journal.alias(address))
        assertNotEquals(alias, journal.alias("other"))
        journal.add(1, "HFP $alias connected")
        assertFalse(journal.text().contains(address))
        assertEquals("device-1", EventJournal().alias("other"))
    }
    @Test fun journalDropsOldEventsAtCapacity() {
        val journal = EventJournal(2)
        journal.add(1, "old")
        journal.add(2, "retained")
        journal.add(3, "latest")
        assertFalse(journal.text().contains("old"))
        assertTrue(journal.text().contains("+2s retained"))
        assertTrue(journal.text().contains("+3s latest"))
    }
}

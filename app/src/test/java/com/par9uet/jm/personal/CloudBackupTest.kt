package com.par9uet.jm.personal

import com.google.gson.JsonParser
import com.par9uet.jm.search.SearchBrowseHistory
import com.par9uet.jm.search.SearchBrowsePosition
import com.par9uet.jm.search.SearchBrowseQuery
import com.par9uet.jm.search.SearchBrowseRecord
import org.junit.Assert.*
import org.junit.Test

class CloudBackupTest {
    @Test fun versionTwoBackupIncludesSearchPositionAndReadsOldBackups() {
        val query = SearchBrowseQuery("标签", from = "2026-10-01", until = "2026-10-18")
        val record = SearchBrowseRecord(query, SearchBrowsePosition(12, 8, 25, 123), 10)
        val library = PersonalLibrary({ emptyMap() }, {})
        val backup = decodePersonalBackup(library.exportBackup(emptyMap(), mapOf(query.key() to record)))
        assertEquals(record, backup.searchBrowseHistory.getValue(query.key()))
        assertEquals(2, backup.version)
        assertTrue(decodePersonalBackup("""{"format":"jm-personal-library","version":1,"records":{},"readHistory":{}}""").searchBrowseHistory.isEmpty())
    }

    @Test fun restoredSearchesMergeUsingMostRecentPosition() {
        val query = SearchBrowseQuery("标签")
        val current = SearchBrowseRecord(query, SearchBrowsePosition(page = 5), 20)
        var persisted = mapOf(query.key() to current)
        val history = SearchBrowseHistory({ persisted }, { persisted = it })
        history.restore(mapOf(query.key() to current.copy(position = SearchBrowsePosition(page = 2), updatedAt = 10)))
        assertEquals(5, history.find(query)?.position?.page)
        val other = SearchBrowseQuery("另一个标签")
        history.restore(mapOf(other.key() to SearchBrowseRecord(other, SearchBrowsePosition(page = 9), 30)))
        assertEquals(9, persisted.getValue(other.key()).position.page)
    }

    @Test fun invalidSearchPositionIsRejectedBeforeRestore() {
        val root = JsonParser.parseString("""{"format":"jm-personal-library","version":2,"records":{},"readHistory":{},"searchBrowseHistory":{}}""").asJsonObject
        val query = SearchBrowseQuery("标签")
        val record = SearchBrowseRecord(query, SearchBrowsePosition(page = -1), 1)
        root.getAsJsonObject("searchBrowseHistory").add(query.key(), com.google.gson.Gson().toJsonTree(record))
        assertThrows(IllegalArgumentException::class.java) { decodePersonalBackup(root.toString()) }
    }
}

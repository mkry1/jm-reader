package com.par9uet.jm.search

import org.junit.Assert.*
import org.junit.Test

class SearchBrowseHistoryTest {
    @Test fun positionSurvivesRestartAndDifferentSearchesDoNotOverwriteIt() {
        var saved = emptyMap<String, SearchBrowseRecord>()
        val history = SearchBrowseHistory({ saved }, { saved = it })
        val query = SearchBrowseQuery("标签甲", order = "NEWEST")
        history.save(query, SearchBrowsePosition(page = 12, index = 7, offset = 36, anchorId = 123))
        history.save(query.copy(text = "标签乙"), SearchBrowsePosition(page = 2))
        assertEquals(SearchBrowsePosition(12, 7, 36, 123), SearchBrowseHistory({ saved }, {}).find(query)?.position)
    }

    @Test fun orderExclusionsAndPublicationRangeHaveIndependentProgress() {
        val history = SearchBrowseHistory({ emptyMap() }, {})
        val base = SearchBrowseQuery("标签甲")
        val variants = listOf(base, base.copy(order = "MOST_LIKE_COUNT"), base.copy(excludedTags = listOf("乙")), base.copy(from = "2026-10-01", until = "2026-10-18"))
        variants.forEachIndexed { i, query -> history.save(query, SearchBrowsePosition(page = i + 1)) }
        assertEquals(listOf(1, 2, 3, 4), variants.map { history.find(it)?.position?.page })
        history.restart(base)
        assertNull(history.find(base))
        assertEquals(4, history.find(variants.last())?.position?.page)
    }

    @Test fun equivalentExclusionOrderUsesTheSamePositionAndAnchorWinsOverOldIndex() {
        val a = SearchBrowseQuery("  标签甲  ", excludedTags = listOf("乙", "丙"))
        val b = SearchBrowseQuery("标签甲", excludedTags = listOf("丙", "乙", "乙"))
        assertEquals(a.key(), b.key())
        val position = SearchBrowsePosition(3, 1, 20, 88)
        assertEquals(2, position.restoreIndex(listOf(11, 22, 88, 99)))
        assertEquals(1, position.restoreIndex(listOf(11, 22)))
        assertEquals(0, position.restoreIndex(emptyList()))
    }
}

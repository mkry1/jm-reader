package com.par9uet.jm.search

import com.par9uet.jm.data.models.Comic
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PublicationSearchTest {
    @Test fun datesAcceptUnixSecondsAndCalendarDatesButRejectMissingDate() {
        assertEquals(LocalDate.of(2026, 10, 18), parsePublicationDate("1792252800"))
        assertEquals(LocalDate.of(2026, 10, 18), parsePublicationDate("2026-10-18 12:00:00"))
        assertNull(parsePublicationDate(""))
    }

    @Test fun dateRangeIncludesBothBoundaryDaysAndStopsAtOlderWorks() = runBlocking {
        val dates = mapOf(1 to "2026-10-18", 2 to "2026-10-17", 3 to "2026-10-16", 4 to "2026-10-15", 5 to "2026-10-14", 6 to "2026-10-13")
        val search = PublicationSearch(
            PublicationRange("2026-10-15", "2026-10-17"),
            fetchPage = { page -> PublicationPage(((page - 1) * 3 + 1..page * 3).map { Comic.create(it, "$it", emptyList()) }, 2) },
            fetchDate = { LocalDate.parse(dates.getValue(it.id)) },
        )
        assertEquals(listOf(2, 3), search.load(1).comics.map { it.id })
        val second = search.load(2)
        assertEquals(listOf(4), second.comics.map { it.id })
        assertNull(second.nextPage)
    }

    @Test fun oldDateRangeSeeksWithoutScanningEveryNewerPage() = runBlocking {
        val requests = mutableListOf<Int>()
        val origin = LocalDate.of(2026, 10, 18)
        val search = PublicationSearch(
            PublicationRange(origin.minusDays(701).toString(), origin.minusDays(700).toString()),
            fetchPage = { page ->
                requests += page
                PublicationPage(((page - 1) * 10..page * 10 - 1).map { Comic.create(it + 1, "$it", emptyList()) }, 100)
            },
            fetchDate = { origin.minusDays((it.id - 1).toLong()) },
        )
        assertEquals(listOf(701, 702), search.load(1).comics.map { it.id })
        assertTrue("不应扫描前面的70页", requests.size <= 10)
    }
}

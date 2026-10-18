package com.par9uet.jm.search

import com.par9uet.jm.data.models.Comic
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

fun parsePublicationDate(raw: String?): LocalDate? {
    val text = raw?.trim().orEmpty()
    return runCatching {
        text.toLongOrNull()?.takeIf { it > 0 }?.let {
            Instant.ofEpochMilli(if (it < 100_000_000_000L) it * 1000 else it)
                .atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
        } ?: LocalDate.parse(text.take(10))
    }.getOrNull()
}

data class PublicationRange(val from: String, val until: String) {
    init { require(!LocalDate.parse(from).isAfter(LocalDate.parse(until))) { "开始日期不能晚于结束日期" } }
    fun contains(date: LocalDate): Boolean = !date.isBefore(LocalDate.parse(from)) && !date.isAfter(LocalDate.parse(until))
}
data class PublicationPage(val comics: List<Comic>, val totalPages: Int)
data class PublicationSearchResult(val comics: List<Comic>, val nextPage: Int?)

/** 仅用于最新上架排序：先定位日期区间，再逐页筛选。 */
class PublicationSearch(
    private val range: PublicationRange,
    private val fetchPage: suspend (Int) -> PublicationPage,
    private val fetchDate: suspend (Comic) -> LocalDate,
) {
    private val pages = mutableMapOf<Int, PublicationPage>()
    private var startPage: Int? = null
    private val concurrency = Semaphore(4)
    private suspend fun fetch(page: Int): PublicationPage = pages[page]
        ?: fetchPage(page).also { pages[page] = it }

    private suspend fun seekStart(): Int {
        val first = fetch(1)
        if (first.comics.isEmpty()) return 1
        val end = LocalDate.parse(range.until)
        if (!fetchDate(first.comics.last()).isAfter(end)) return 1
        var low = 2
        var high = first.totalPages
        while (low <= high) {
            val middle = low + (high - low) / 2
            val comics = fetch(middle).comics
            if (comics.isEmpty() || !fetchDate(comics.last()).isAfter(end)) high = middle - 1
            else low = middle + 1
        }
        return low
    }

    suspend fun load(page: Int): PublicationSearchResult {
        val start = startPage ?: seekStart().also { startPage = it }
        if (start > fetch(1).totalPages) return PublicationSearchResult(emptyList(), null)
        val actualPage = start + page - 1
        val result = fetch(actualPage)
        val dated = coroutineScope {
            result.comics.map { comic -> async { concurrency.withPermit { comic to fetchDate(comic) } } }.awaitAll()
        }
        val hasOlder = dated.any { (_, date) -> date.isBefore(LocalDate.parse(range.from)) }
        return PublicationSearchResult(
            dated.filter { (_, date) -> range.contains(date) }.map { it.first },
            (page + 1).takeIf { !hasOlder && actualPage < result.totalPages && result.comics.isNotEmpty() },
        )
    }
}

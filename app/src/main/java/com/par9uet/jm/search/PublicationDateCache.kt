package com.par9uet.jm.search

import com.par9uet.jm.data.models.Comic
import com.par9uet.jm.retrofit.model.ComicDetailResponse
import com.par9uet.jm.retrofit.model.NetWorkResult
import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PublicationDateCache(
    private val fetchDetail: suspend (Int) -> NetWorkResult<ComicDetailResponse>,
    load: () -> Map<String, String>,
    private val persist: (Map<String, String>) -> Unit,
) {
    private val cache = load().toMutableMap()
    private val mutex = Mutex()
    suspend fun date(comic: Comic, source: String): LocalDate {
        val key = "$source|${comic.id}"
        mutex.withLock { parsePublicationDate(cache[key]) }?.let { return it }
        val date = when (val result = fetchDetail(comic.id)) {
            is NetWorkResult.Success -> parsePublicationDate(result.data.addtime)
                ?: error("无法确认部分作品的上架日期，请重试")
            is NetWorkResult.Error -> error(result.message)
        }
        mutex.withLock {
            cache[key] = date.toString()
            persist(cache.toMap())
        }
        return date
    }
}

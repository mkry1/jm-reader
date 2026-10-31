package com.par9uet.jm.store

import com.par9uet.jm.data.models.Comic
import com.par9uet.jm.storage.ComicReadHistory
import com.par9uet.jm.storage.ReadHistoryStorage
import com.par9uet.jm.storage.ReaderPosition
import com.par9uet.jm.task.AppInitTask
import com.par9uet.jm.task.AppTaskInfo
import com.par9uet.jm.utils.log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ReadHistoryManager(
    private val load: () -> Map<Int, ComicReadHistory>,
    private val persist: (Map<Int, ComicReadHistory>, Boolean) -> Unit,
) : AppInitTask {
    constructor(storage: ReadHistoryStorage) : this(storage::get, { history, sync -> storage.set(history, sync) })

    // 进度恢复不等待登录、远程配置等初始化任务。
    private val _readHistoryState = MutableStateFlow(load())
    val readHistoryState = _readHistoryState.asStateFlow()

    fun historyKey(comic: Comic?, fallbackId: Int): Int {
        return comic?.seriesId
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: comic?.id
            ?: fallbackId
    }

    fun markRead(comic: Comic?, chapterId: Int): Int {
        return markRead(historyKey(comic, chapterId), chapterId)
    }

    fun markRead(comicKey: Int, chapterId: Int): Int {
        val current = _readHistoryState.value.toMutableMap()
        val old = current[comicKey]
        val readIds = (old?.readChapterIds.orEmpty() + chapterId).distinct()
        current[comicKey] = (old?.takeIf { it.lastChapterId == chapterId } ?: ComicReadHistory()).copy(
            lastChapterId = chapterId,
            readChapterIds = readIds,
        )
        _readHistoryState.update { current }
        persist(current, false)
        return comicKey
    }

    fun saveReadProgress(
        comicKey: Int, chapterId: Int, pageIndex: Int, pageCount: Int,
        scrollOffset: Int = 0, synchronous: Boolean = false,
    ) {
        val current = _readHistoryState.value.toMutableMap()
        val old = current[comicKey]
        val readIds = (old?.readChapterIds.orEmpty() + chapterId).distinct()
        current[comicKey] = ComicReadHistory(
            lastChapterId = chapterId,
            readChapterIds = readIds,
            lastPageIndex = pageIndex,
            lastChapterPageCount = pageCount,
            lastPageScrollOffset = scrollOffset.coerceAtLeast(0),
        )
        _readHistoryState.update { current }
        persist(current, synchronous)
    }

    fun readChapterIds(
        comicKey: Int,
        history: Map<Int, ComicReadHistory> = _readHistoryState.value
    ): Set<Int> {
        return history[comicKey]?.readChapterIds.orEmpty().toSet()
    }

    fun lastReadChapterId(
        comic: Comic,
        history: Map<Int, ComicReadHistory> = _readHistoryState.value
    ): Int? {
        val lastId = history[historyKey(comic, comic.id)]?.lastChapterId?.takeIf { it > 0 }
        if (lastId == null || comic.comicChapterList.isEmpty()) {
            return lastId
        }
        return lastId.takeIf { id -> comic.comicChapterList.any { it.id == id } }
    }

    fun lastReadPageIndex(
        comicKey: Int,
        chapterId: Int,
        history: Map<Int, ComicReadHistory> = _readHistoryState.value
    ): Int {
        return lastReadPosition(comicKey, chapterId, history).pageIndex
    }

    fun lastReadPosition(
        comicKey: Int, chapterId: Int,
        history: Map<Int, ComicReadHistory> = _readHistoryState.value,
    ): ReaderPosition {
        val entry = history[comicKey] ?: return ReaderPosition()
        if (entry.lastChapterId != chapterId || entry.lastChapterPageCount <= 0) return ReaderPosition()
        return ReaderPosition(entry.lastPageIndex, entry.lastPageScrollOffset).bounded(entry.lastChapterPageCount)
    }

    /** Finds the persisted series key before chapter detail has finished loading. */
    fun historyKeyForChapter(
        chapterId: Int,
        history: Map<Int, ComicReadHistory> = _readHistoryState.value,
    ): Int {
        return history.entries.firstOrNull { (_, entry) ->
            entry.lastChapterId == chapterId || chapterId in entry.readChapterIds
        }?.key ?: chapterId
    }

    fun restoreReadHistory(imported: Map<Int, ComicReadHistory>) {
        val merged = imported + _readHistoryState.value
        persist(merged, false)
        _readHistoryState.value = merged
    }

    override suspend fun init() {
        log("加载阅读历史")
        _readHistoryState.update { load() }
        log("阅读历史已加载")
    }

    private val appTaskInfo = AppTaskInfo(
        taskName = "加载阅读历史",
        sort = 5,
    )

    override fun getAppTaskInfo(): AppTaskInfo = appTaskInfo
}

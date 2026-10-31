package com.par9uet.jm.personal

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.par9uet.jm.data.models.Comic
import com.par9uet.jm.storage.ComicReadHistory
import com.par9uet.jm.search.SearchBrowseRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

fun Comic.personalId(): Int = seriesId.toIntOrNull()?.takeIf { it > 0 } ?: id

data class PersonalComic(
    val comic: Comic,
    val read: Boolean = false,
    val liked: Boolean = false,
    val dismissed: Boolean = false,
    val updatedAt: Long = 0,
)

data class PersonalBackup(
    val format: String = "jm-personal-library",
    val version: Int = 2,
    val records: Map<Int, PersonalComic> = emptyMap(),
    val readHistory: Map<Int, ComicReadHistory> = emptyMap(),
    val searchBrowseHistory: Map<String, SearchBrowseRecord> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
)

fun decodePersonalBackup(json: String): PersonalBackup {
    try {
        val root = JsonParser.parseString(json).asJsonObject
        require(root.get("format")?.asString == "jm-personal-library") { "请选择本应用导出的阅读记录备份" }
        val version = root.get("version")?.asInt
        require(version == 1 || version == 2) { "暂不支持此备份版本" }
        require(root.get("records")?.isJsonObject == true && root.get("readHistory")?.isJsonObject == true) { "备份内容不完整" }
        if (version == 2) require(root.get("searchBrowseHistory")?.isJsonObject == true) { "备份缺少搜索进度" }
        val backup = Gson().fromJson(root, PersonalBackup::class.java)
        for ((id, entry) in backup.records) {
            requireNotNull(entry) { "备份作品记录为空" }
            val comic = requireNotNull(entry.comic) { "备份缺少作品信息" }
            require(id > 0 && comic.personalId() == id) { "备份作品编号不正确" }
            requireNotNull(comic.name) { "备份缺少作品名称" }
            requireNotNull(comic.coverUrl) { "备份缺少封面信息" }
            for (list in listOf(comic.authorList, comic.tagList, comic.roleList, comic.workList, comic.typeList)) {
                requireNotNull(list) { "备份作品列表不完整" }
                require(list.filterNotNull().size == list.size) { "备份作品列表含有空项" }
            }
        }
        for ((id, progress) in backup.readHistory) {
            requireNotNull(progress) { "备份阅读进度为空" }
            require(id > 0 && progress.lastChapterId >= 0 && progress.lastPageIndex >= 0 && progress.lastChapterPageCount >= 0 && progress.lastPageScrollOffset >= 0) { "备份阅读进度不正确" }
            requireNotNull(progress.readChapterIds) { "备份章节记录为空" }
            require(progress.readChapterIds.all { it > 0 }) { "备份章节编号不正确" }
        }
        for ((key, record) in backup.searchBrowseHistory) {
            requireNotNull(record) { "备份搜索记录为空" }
            val query = requireNotNull(record.query) { "备份搜索条件为空" }
            val position = requireNotNull(record.position) { "备份搜索位置为空" }
            requireNotNull(query.text) { "备份搜索词为空" }
            requireNotNull(query.order) { "备份搜索排序为空" }
            requireNotNull(query.excludedTags) { "备份搜索排除标签为空" }
            require(key == query.key() && position.page > 0 && position.index >= 0 && position.offset >= 0) { "备份搜索位置不正确" }
        }
        return backup
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Exception) {
        throw IllegalArgumentException("备份文件无法读取", e)
    }
}

/** 本地已读状态与阅读进度分开；无需登录，也不向推荐服务上传记录。 */
class PersonalLibrary(
    load: () -> Map<Int, PersonalComic>,
    private val save: (Map<Int, PersonalComic>) -> Unit,
) {
    private val mutableRecords = MutableStateFlow(load())
    val records = mutableRecords.asStateFlow()

    @Synchronized
    private fun update(comic: Comic, change: (PersonalComic) -> PersonalComic) {
        val key = comic.personalId()
        val old = mutableRecords.value[key] ?: PersonalComic(comic)
        val next = change(old).copy(
            comic = comic.copy(
                id = key,
                authorList = comic.authorList.filter { it.isNotBlank() }.ifEmpty { old.comic.authorList },
                tagList = comic.tagList.ifEmpty { old.comic.tagList },
                coverUrl = comic.coverUrl.ifBlank { old.comic.coverUrl },
                relateComicList = emptyList(), comicChapterList = emptyList(),
            ),
            updatedAt = System.currentTimeMillis(),
        )
        val updated = mutableRecords.value + (key to next)
        save(updated)
        mutableRecords.value = updated
    }

    fun toggleRead(comic: Comic) = update(comic) { it.copy(read = !it.read) }
    fun toggleLiked(comic: Comic) = update(comic) { it.copy(liked = !it.liked, dismissed = false) }
    fun toggleDismissed(comic: Comic) = update(comic) { it.copy(dismissed = !it.dismissed, liked = false) }

    fun allows(comic: Comic): Boolean {
        val record = records.value[comic.personalId()] ?: records.value[comic.id]
        return record == null || (!record.read && !record.dismissed)
    }

    fun recommend(candidates: List<Comic>): List<Comic> {
        val liked = records.value.values.filter { it.liked && !it.dismissed }
        val authors = liked.flatMap { it.comic.authorList }.filter { it.isNotBlank() }.groupingBy { it.trim() }.eachCount()
        val tags = liked.flatMap { it.comic.tagList }.filter { it.isNotBlank() }.groupingBy { it.trim() }.eachCount()
        return candidates.distinctBy { it.personalId() }.filter(::allows).sortedByDescending { comic ->
            comic.authorList.distinct().sumOf { 3 * (authors[it.trim()] ?: 0) } +
                comic.tagList.distinct().sumOf { tags[it.trim()] ?: 0 }
        }
    }

    fun exportBackup(
        history: Map<Int, ComicReadHistory>,
        searches: Map<String, SearchBrowseRecord> = emptyMap(),
    ): String = Gson().toJson(PersonalBackup(records = records.value, readHistory = history, searchBrowseHistory = searches))

    @Synchronized
    fun restore(backup: PersonalBackup) {
        val merged = mutableRecords.value.toMutableMap()
        for ((id, incoming) in backup.records) {
            if (incoming.updatedAt >= (merged[id]?.updatedAt ?: 0)) merged[id] = incoming
        }
        save(merged)
        mutableRecords.value = merged
    }
}

package com.par9uet.jm.search

import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SearchBrowseQuery(
    val text: String,
    val excludedTags: List<String> = emptyList(),
    val order: String = "NEWEST",
    val mainTag: Int = 0,
    val source: String = "",
    val from: String? = null,
    val until: String? = null,
) {
    fun key(): String = Gson().toJson(copy(
        text = text.trim().replace(Regex("\\s+"), " "),
        excludedTags = excludedTags.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted(),
    ))
}

data class SearchBrowsePosition(
    val page: Int = 1,
    val index: Int = 0,
    val offset: Int = 0,
    val anchorId: Int? = null,
) {
    fun restoreIndex(ids: List<Int>): Int = ids.indexOf(anchorId).takeIf { it >= 0 }
        ?: index.coerceIn(0, (ids.size - 1).coerceAtLeast(0))
}

data class SearchBrowseRecord(val query: SearchBrowseQuery, val position: SearchBrowsePosition, val updatedAt: Long)

class SearchBrowseHistory(
    load: () -> Map<String, SearchBrowseRecord>,
    private val persist: (Map<String, SearchBrowseRecord>) -> Unit,
) {
    private val mutableRecords = MutableStateFlow(load())
    val records = mutableRecords.asStateFlow()
    fun find(query: SearchBrowseQuery): SearchBrowseRecord? = records.value[query.key()]
    fun save(query: SearchBrowseQuery, position: SearchBrowsePosition) {
        val updated = records.value + (query.key() to SearchBrowseRecord(query, position, System.currentTimeMillis()))
        persist(updated)
        mutableRecords.value = updated
    }
    fun restart(query: SearchBrowseQuery) {
        val updated = records.value - query.key()
        persist(updated)
        mutableRecords.value = updated
    }
    fun clear() {
        persist(emptyMap())
        mutableRecords.value = emptyMap()
    }
}

package com.par9uet.jm.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.par9uet.jm.data.models.Comic
import com.par9uet.jm.data.models.ComicSearchOrderFilter
import com.par9uet.jm.personal.PersonalComic
import com.par9uet.jm.repository.ComicRepository
import com.par9uet.jm.retrofit.model.NetWorkResult
import io.github.jukomu.jmcomic.api.enums.SearchMainTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PersonalRecommendationViewModel(private val repository: ComicRepository) : ViewModel() {
    data class State(val comics: List<Comic> = emptyList(), val loading: Boolean = false, val message: String = "")
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    fun refresh(likes: List<PersonalComic>) {
        job?.cancel()
        job = viewModelScope.launch {
            mutableState.value = State(loading = true)
            val candidates = mutableListOf<Comic>()
            var failures = 0
            val authors = likes.flatMap { it.comic.authorList }.filter { it.isNotBlank() }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(2)
            val tags = likes.flatMap { it.comic.tagList }.filter { it.isNotBlank() }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
            try {
                // 少量标签和作者查询即可形成个人候选池，不扫描整站。
                for ((query, mainTag) in authors.map { it.key to SearchMainTag.AUTHOR.value } +
                    tags.map { it.key to SearchMainTag.TAG.value }) {
                    when (val result = repository.getComicListWithMainTag(1, ComicSearchOrderFilter.MOST_LIKE_COUNT, query, mainTag)) {
                        is NetWorkResult.Success -> candidates += result.data.toComicList().map { comic ->
                            if (mainTag == SearchMainTag.TAG.value) comic.copy(tagList = (comic.tagList + query).distinct()) else comic
                        }
                        is NetWorkResult.Error -> failures++
                    }
                }
                mutableState.value = State(comics = candidates, message = if (failures > 0) "部分偏好查询失败，可刷新重试" else "")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.value = State(comics = candidates, message = "偏好推荐暂时无法加载，可刷新重试")
            }
        }
    }
}

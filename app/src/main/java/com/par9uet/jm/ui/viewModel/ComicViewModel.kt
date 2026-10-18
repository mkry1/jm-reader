package com.par9uet.jm.ui.viewModel

import com.par9uet.jm.search.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.par9uet.jm.data.models.ComicSearchOrderFilter
import com.par9uet.jm.data.models.ComicRankCategory
import com.par9uet.jm.data.models.ComicRankPeriod
import com.par9uet.jm.data.models.ComicRankingFilter
import com.par9uet.jm.data.models.HomeComicSwiperItem
import com.par9uet.jm.data.models.WeekData
import com.par9uet.jm.repository.ComicRepository
import com.par9uet.jm.repository.ComicTagFilter
import com.par9uet.jm.retrofit.model.HomeSwiperComicListItemResponse
import com.par9uet.jm.retrofit.model.NetWorkResult
import com.par9uet.jm.retrofit.model.WeekResponse
import com.par9uet.jm.store.LocalSettingManager
import com.par9uet.jm.ui.models.CommonUIState
import com.par9uet.jm.ui.pagingSource.SearchComicFilter
import com.par9uet.jm.ui.pagingSource.ComicPageController
import com.par9uet.jm.ui.pagingSource.SearchComicPagingSource
import com.par9uet.jm.ui.pagingSource.ComicRankingPagingSource
import com.par9uet.jm.ui.pagingSource.WeekComicPagingSource
import com.par9uet.jm.ui.pagingSource.WeekFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class ComicViewModel(
    private val comicRepository: ComicRepository,
    private val localSettingManager: LocalSettingManager,
    private val comicTagFilter: ComicTagFilter,
    private val personalLibrary: com.par9uet.jm.personal.PersonalLibrary,
    private val searchHistory: SearchBrowseHistory,
    private val publicationDates: PublicationDateCache,
) : ViewModel() {
    data class HomeComicUIState(
        val isLoading: Boolean = true,
        val isError: Boolean = false,
        val list: List<HomeComicSwiperItem> = listOf(),
        val errorMsg: String? = null
    )

    private val _homeComicState = MutableStateFlow(HomeComicUIState())
    val homeComicState = _homeComicState.asStateFlow()
    private var homeJob: Job? = null
    private var homeSource: String? = null
    fun getHomeComic(forceRefresh: Boolean = true) {
        val source = localSettingManager.localSettingState.value.comicApiSource
        if (!forceRefresh && homeSource == source &&
            (homeJob?.isActive == true || _homeComicState.value.list.isNotEmpty())
        ) return
        homeJob?.cancel()
        homeSource = source
        homeJob = viewModelScope.launch {
            _homeComicState.update {
                it.copy(
                    isLoading = true,
                    isError = false,
                    errorMsg = ""
                )
            }
            when (val data = comicRepository.getHomeSwiperComicList { firstContent ->
                _homeComicState.update { state ->
                    if (state.list.isEmpty()) state.copy(
                        list = firstContent.map { it.toHomeComicSwiperItem() },
                        isError = false,
                    ) else state
                }
            }) {
                is NetWorkResult.Error -> {
                    _homeComicState.update {
                        it.copy(isError = true, errorMsg = data.message)
                    }
                }

                is NetWorkResult.Success<List<HomeSwiperComicListItemResponse>> -> {
                    _homeComicState.update {
                        it.copy(list = data.data.map { item -> item.toHomeComicSwiperItem() })
                    }
                }
            }
            _homeComicState.update {
                it.copy(isLoading = false)
            }
        }
    }

    private val _searchComicFilterState = MutableStateFlow(SearchComicFilter())
    val searchComicFilterState = _searchComicFilterState.asStateFlow()
    private val _searchComicIdState = MutableStateFlow<Int?>(null)
    val searchComicIdState = _searchComicIdState.asStateFlow()

    data class LoadedSearchPage(val query: SearchBrowseQuery, val page: Int, val ids: List<Int>)
    private val mutableLoadedSearchPage = MutableStateFlow<LoadedSearchPage?>(null)
    val loadedSearchPage = mutableLoadedSearchPage.asStateFlow()
    private var activeSearchEntry: String? = null
    private val entryFilters = mutableMapOf<String, SearchComicFilter>()

    private fun SearchComicFilter.browseQuery() = SearchBrowseQuery(
        text = searchContent, excludedTags = excludedTags, order = order.name, mainTag = mainTag,
        source = apiSource, from = publicationRange?.from, until = publicationRange?.until,
    )
    fun currentSearchQuery(): SearchBrowseQuery {
        val setting = localSettingManager.localSettingState.value
        return _searchComicFilterState.value.copy(
            excludedTags = (_searchComicFilterState.value.excludedTags + setting.blockedTagList).distinct(),
            apiSource = "${setting.comicApiSource}|${setting.api}",
        ).browseQuery()
    }
    fun rememberSearchPosition(query: SearchBrowseQuery, position: SearchBrowsePosition, ids: List<Int>) {
        val loaded = mutableLoadedSearchPage.value ?: return
        if (!searchPagination.state.value.isLoading && loaded.query.key() == query.key() &&
            loaded.page == position.page && loaded.ids == ids && ids.isNotEmpty()) {
            searchHistory.save(query, position)
        }
    }
    fun restartSearch() {
        searchHistory.restart(currentSearchQuery())
        mutableLoadedSearchPage.value = null
        searchPagination.restart()
    }
    fun openSearch(entry: String, text: String, excludedTags: List<String>, mainTag: Int, savedQuery: SearchBrowseQuery? = null) {
        if (activeSearchEntry == entry) return
        activeSearchEntry?.let { entryFilters[it] = _searchComicFilterState.value }
        activeSearchEntry = entry
        val allExclusions = (excludedTags + localSettingManager.localSettingState.value.blockedTagList).distinct().sorted()
        val query = savedQuery ?: searchHistory.records.value.values
            .filter { it.query.text == text && it.query.mainTag == mainTag && it.query.excludedTags.distinct().sorted() == allExclusions }
            .maxByOrNull { it.updatedAt }?.query
        val previousFilter = _searchComicFilterState.value
        _searchComicFilterState.value = entryFilters[entry] ?: SearchComicFilter(
            searchContent = text, excludedTags = excludedTags, mainTag = mainTag,
            order = query?.order?.let { runCatching { ComicSearchOrderFilter.valueOf(it) }.getOrNull() } ?: ComicSearchOrderFilter.NEWEST,
            publicationRange = query?.from?.let { from -> query.until?.let { PublicationRange(from, it) } },
        )
        if (previousFilter != _searchComicFilterState.value) mutableLoadedSearchPage.value = null
    }
    fun changePublicationRange(range: PublicationRange?) {
        _searchComicIdState.value = null
        _searchComicFilterState.update { it.copy(publicationRange = range, order = ComicSearchOrderFilter.NEWEST) }
    }

    val searchPagination = ComicPageController()
    val searchComicPager = searchPagination.pagingFlow(
        filters = combine(_searchComicFilterState, localSettingManager.localSettingState) { filter, setting ->
            filter.copy(
                excludedTags = (filter.excludedTags + setting.blockedTagList).distinct(),
                    apiSource = "${setting.comicApiSource}|${setting.api}",
            )
        },
        initialPage = { searchHistory.find(it.browseQuery())?.position?.page ?: 1 },
        onPageLoaded = { filter, state, comics ->
            mutableLoadedSearchPage.value = LoadedSearchPage(filter.browseQuery(), state.page, comics.map { it.id })
        },
    ) { filter ->
        SearchComicPagingSource(
            comicRepository = comicRepository,
            filter = filter,
            onFindSingleComicId = { id -> _searchComicIdState.value = id },
            tagFilter = comicTagFilter,
            tagSource = filter.apiSource,
            dateCache = publicationDates,
        )
    }.cachedIn(viewModelScope)

    fun changeSearchComicOrderFilter(order: ComicSearchOrderFilter) {
        _searchComicIdState.update { null }
        _searchComicFilterState.update {
            it.copy(
                order = order,
                publicationRange = if (order == ComicSearchOrderFilter.NEWEST) it.publicationRange else null,
            )
        }
    }

    fun changeSearchComicContent(searchContent: String) {
        _searchComicIdState.update { null }
        _searchComicFilterState.update {
            it.copy(
                searchContent = searchContent
            )
        }
    }

    fun changeSearchComicContent(
        searchContent: String,
        excludedTags: List<String>,
        mainTag: Int = 0,
    ) {
        _searchComicIdState.update { null }
        _searchComicFilterState.update {
            it.copy(
                searchContent = searchContent,
                excludedTags = excludedTags,
                mainTag = mainTag,
            )
        }
    }

    fun consumeSearchComicId() {
        _searchComicIdState.update { null }
    }

    private val _comicRankingFilterState = MutableStateFlow<ComicRankingFilter?>(null)
    val comicRankingFilterState = _comicRankingFilterState.asStateFlow()

    val rankingPagination = ComicPageController()
    val comicRankingPager = rankingPagination.pagingFlow(
        filters = combine(
            _comicRankingFilterState.filterNotNull(),
            localSettingManager.localSettingState,
        ) { filter, setting -> Triple(filter, setting.blockedTagList.distinct(), setting.comicApiSource) },
    ) { (filter, blockedTags, _) ->
        ComicRankingPagingSource(comicRepository, filter, blockedTags)
    }.cachedIn(viewModelScope)

    fun setComicRankingFilter(
        tagName: String,
        category: ComicRankCategory?,
        period: ComicRankPeriod,
    ) {
        _comicRankingFilterState.value = ComicRankingFilter(
            tagName = tagName,
            category = category,
            period = period,
        )
    }

    private val _weekDataState = MutableStateFlow(CommonUIState<WeekData>())
    val weekDataState = _weekDataState.asStateFlow()
    fun getWeekData() {
        viewModelScope.launch {
            _weekDataState.update {
                it.copy(
                    isLoading = true,
                    isError = false,
                    errorMsg = ""
                )
            }
            when (val data = comicRepository.getWeekData()) {
                is NetWorkResult.Error -> {
                    _weekDataState.update {
                        it.copy(isError = true, errorMsg = data.message)
                    }
                }

                is NetWorkResult.Success<WeekResponse> -> {
                    val d = data.data.toWeekData()
                    _weekDataState.update {
                        it.copy(data = d)
                    }
                    if (d.categoryList.isNotEmpty()) {
                        _weekFilterState.update {
                            it.copy(categoryId = d.categoryList[0].first)
                        }
                    }
                    if (d.typeList.isNotEmpty()) {
                        _weekFilterState.update {
                            it.copy(typeId = d.typeList[0].first)
                        }
                    }
                }
            }
            _weekDataState.update {
                it.copy(isLoading = false)
            }
        }
    }

    private val _weekFilterState = MutableStateFlow(WeekFilter())
    val weekFilterState = _weekFilterState.asStateFlow()

    val weekPagination = ComicPageController()
    val weekComicPager = weekPagination.pagingFlow(
        filters = combine(_weekFilterState, localSettingManager.localSettingState, personalLibrary.records) { filter, setting, records ->
            Triple(filter, (setting.blockedTagList + setting.homeExcludedTags).distinct(), "${setting.comicApiSource}|${setting.api}") to
                records.filterValues { it.read || it.dismissed }.keys
        },
    ) { (query, excludedIds) ->
        val (filter, blockedTags, source) = query
        WeekComicPagingSource(
            comicRepository = comicRepository,
            filter = filter,
            blockedTagList = blockedTags,
            tagFilter = comicTagFilter,
            tagSource = source,
            excludedIds = excludedIds,
        )
    }.cachedIn(viewModelScope)

    fun changeWeekCategoryFilter(categoryId: String?) {
        _weekFilterState.update {
            it.copy(
                categoryId = categoryId
            )
        }
    }

    fun changeWeekTypeFilter(typeId: String?) {
        _weekFilterState.update {
            it.copy(
                typeId = typeId
            )
        }
    }
}

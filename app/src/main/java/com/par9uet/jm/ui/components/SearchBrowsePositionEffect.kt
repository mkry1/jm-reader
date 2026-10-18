package com.par9uet.jm.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.par9uet.jm.data.models.Comic
import com.par9uet.jm.search.SearchBrowseHistory
import com.par9uet.jm.search.SearchBrowsePosition
import com.par9uet.jm.search.SearchBrowseQuery
import com.par9uet.jm.ui.pagingSource.ComicPageState
import com.par9uet.jm.ui.viewModel.ComicViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

@Composable
fun rememberSearchBrowseGridState(
    query: SearchBrowseQuery,
    pageState: ComicPageState,
    loaded: ComicViewModel.LoadedSearchPage?,
    items: LazyPagingItems<Comic>,
    history: SearchBrowseHistory,
    reset: Int,
    onSave: (SearchBrowseQuery, SearchBrowsePosition, List<Int>) -> Unit,
): LazyGridState {
    val key = query.key()
    val position = remember(key, pageState.page, reset) { history.find(query)?.position?.takeIf { it.page == pageState.page } }
    val grid = rememberSaveable(key, pageState.page, reset, saver = LazyGridState.Saver) { LazyGridState() }
    val latestPage by rememberUpdatedState(pageState)
    val latestLoaded by rememberUpdatedState(loaded)
    val restored = remember(key, pageState.page, reset, grid) { mutableStateOf(false) }
    fun ready(): Boolean {
        val result = latestLoaded ?: return false
        return !latestPage.isLoading && result.query.key() == key && result.page == pageState.page &&
            items.loadState.refresh is LoadState.NotLoading && result.ids.isNotEmpty() &&
            items.itemSnapshotList.items.map { it.id } == result.ids
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(key, pageState.page, reset, grid, owner) {
        fun saveBeforeLeaving() {
            if (!restored.value || !ready()) return
            val ids = items.itemSnapshotList.items.map { it.id }
            onSave(query, SearchBrowsePosition(
                pageState.page, grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset,
                ids.getOrNull(grid.firstVisibleItemIndex),
            ), ids)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) saveBeforeLeaving()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            saveBeforeLeaving()
            owner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(key, pageState.page, reset, grid) {
        snapshotFlow { ready() }.first { it }
        val ids = items.itemSnapshotList.items.map { it.id }
        if (position != null) grid.scrollToItem(position.restoreIndex(ids), position.offset)
        restored.value = true
        snapshotFlow {
            if (!ready() || grid.isScrollInProgress) null else SearchBrowsePosition(
                page = pageState.page,
                index = grid.firstVisibleItemIndex,
                offset = grid.firstVisibleItemScrollOffset,
                anchorId = items.itemSnapshotList.items.getOrNull(grid.firstVisibleItemIndex)?.id,
            )
        }.filterNotNull().distinctUntilChanged().collect { cursor ->
            onSave(query, cursor, items.itemSnapshotList.items.map { it.id })
        }
    }
    return grid
}

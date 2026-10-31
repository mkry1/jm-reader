package com.par9uet.jm.ui.screens.readScreen

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.par9uet.jm.data.models.Comic
import com.par9uet.jm.data.models.ComicChapter
import com.par9uet.jm.data.models.ImageResultState
import com.par9uet.jm.storage.ReaderPosition
import com.par9uet.jm.store.DownloadManager
import com.par9uet.jm.store.LocalSettingManager
import com.par9uet.jm.store.ReadHistoryManager
import com.par9uet.jm.store.UserManager
import com.par9uet.jm.ui.screens.LocalMainNavController
import com.par9uet.jm.ui.components.adaptiveDialogMaxHeight
import com.par9uet.jm.ui.viewModel.ComicReadViewModel
import com.par9uet.jm.utils.VolumeKeyPageTurnDispatcher
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.getKoin
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ComicReadScreen(
    comicId: Int,
    localOnly: Boolean = false,
    comicReadViewModel: ComicReadViewModel = koinViewModel(),
    localSettingManager: LocalSettingManager = getKoin().get(),
    readHistoryManager: ReadHistoryManager = getKoin().get(),
    downloadManager: DownloadManager = getKoin().get(),
    userManager: UserManager = getKoin().get(),
) {
    val context = LocalContext.current
    val mainNavController = LocalMainNavController.current
    val isShowToolbar by comicReadViewModel.isShowToolBar
    val size = comicReadViewModel.size
    var currentIndexState by comicReadViewModel.currentIndexState
    val localSetting by localSettingManager.localSettingState.collectAsState()
    var brightnessOverride by rememberSaveable { mutableIntStateOf(0) }
    val systemDark = isSystemInDarkTheme()
    val brightnessPercent = readerBrightnessPercent(
        localSetting.readerBrightnessEnabled, systemDark,
        localSetting.readerLightBrightnessPercent, localSetting.readerDarkBrightnessPercent,
        brightnessOverride,
    )
    val dimmingAlpha = readerDimmingAlpha(
        localSetting.readerBrightnessEnabled,
        systemDark,
        localSetting.readerLightBrightnessPercent,
        localSetting.readerDarkBrightnessPercent,
        brightnessOverride,
    )
    val isLogin by userManager.isLoginState.collectAsState(false)
    val comicPicState by comicReadViewModel.comicPicState.collectAsState()
    val comicDetailState by comicReadViewModel.comicDetailState.collectAsState()
    val localChapterList by comicReadViewModel.localChapterList.collectAsState()
    val readHistory by readHistoryManager.readHistoryState.collectAsState()
    val comic = comicDetailState.data
    val loading = comicPicState.isLoading
    val initialReaderIndex = if (size > 0) currentIndexState.coerceIn(0, size - 1) else 0
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialReaderIndex)
    val pagerState = rememberPagerState(initialPage = initialReaderIndex) { size }
    val zoomState = rememberReaderZoomState()
    var targetIndex by remember { mutableIntStateOf(initialReaderIndex) }
    var targetOffset by remember(comicId, localOnly) { mutableIntStateOf(0) }
    val progressSession = remember(comicId, localOnly) { ReaderProgressSession(comicId) }
    var activeDialog by remember { mutableStateOf<ReadPanelDialog?>(null) }
    var selectedCacheChapterIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var loadedComicId by comicReadViewModel.loadedComicId
    val readHistoryComicId by comicReadViewModel.readHistoryComicId
    val readChapterIds = remember(
        readHistory,
        readHistoryComicId,
        localSetting.chapterReadingMemoryEnabled,
    ) {
        if (localSetting.chapterReadingMemoryEnabled && readHistoryComicId > 0) {
            readHistoryManager.readChapterIds(readHistoryComicId, readHistory)
        } else {
            emptySet()
        }
    }
    val readableChapters = if (localOnly) {
        localChapterList
    } else {
        comic?.comicChapterList.orEmpty()
    }
    val chapterIndex = remember(readableChapters, comicId) {
        readableChapters.indexOfFirst { it.id == comicId }
    }
    val previousChapter = remember(readableChapters, chapterIndex) {
        readableChapters.getOrNull(chapterIndex - 1)
    }
    val nextChapter = remember(readableChapters, chapterIndex) {
        readableChapters.getOrNull(chapterIndex + 1)
    }

    fun navigateToChapter(chapter: ComicChapter?) {
        if (chapter == null) return
        val targetRoute = if (localOnly) {
            "localComicRead/${chapter.id}"
        } else {
            "comicRead/${chapter.id}"
        }
        val currentRoute = if (localOnly) "localComicRead/$comicId" else "comicRead/$comicId"
        mainNavController.navigate(targetRoute) {
            popUpTo(currentRoute) {
                inclusive = true
            }
        }
    }

    fun updateIndexFromReader(value: Float) {
        val target = value.toInt().coerceIn(0, maxOf(0, size - 1))
        if (target != currentIndexState && localSetting.readMode != "scroll") {
            zoomState.reset()
        }
        // Keep the toolbar and reading-memory state driven by one observable source.
        currentIndexState = target
    }

    fun jumpToIndex(index: Int) {
        if (size <= 0) return

        val target = index.coerceIn(0, size - 1)
        zoomState.reset()
        currentIndexState = target
        targetIndex = target
        targetOffset = 0
        comicReadViewModel.decodeIndex(target, context)
        comicReadViewModel.showToolBar()
    }

    val volumePageTurnHandler = rememberUpdatedState<(Boolean) -> Unit> { isVolumeUp ->
        val currentIndex = comicReadViewModel.currentIndexState.intValue
        jumpToIndex(if (isVolumeUp) currentIndex - 1 else currentIndex + 1)
    }
    DisposableEffect(comicId, localOnly, localSetting.volumeKeyPageTurningEnabled) {
        if (!localSetting.volumeKeyPageTurningEnabled) {
            onDispose { }
        } else {
            val unregister = VolumeKeyPageTurnDispatcher.register { isVolumeUp ->
                volumePageTurnHandler.value(isVolumeUp)
            }
            onDispose { unregister() }
        }
    }

    LaunchedEffect(comicId, localOnly) {
        val readingMemoryEnabled = localSetting.comicReadingMemoryEnabled
        progressSession.startRestoring()
        val onSuccess: (ReaderPosition) -> Unit = { saved ->
            val position = (if (loadedComicId == comicId && readingMemoryEnabled) {
                saved.copy(pageIndex = currentIndexState)
            } else saved).bounded(comicReadViewModel.size)
            currentIndexState = position.pageIndex
            targetIndex = position.pageIndex
            targetOffset = if (localSetting.readMode == "scroll") position.scrollOffset else 0
            loadedComicId = comicId
            zoomState.reset()
            comicReadViewModel.decodeIndex(targetIndex, context)
        }
        val currentPages = comicReadViewModel.comicPicState.value
        if (loadedComicId == comicId && !currentPages.isLoading && !currentPages.isError && !currentPages.data.isNullOrEmpty()) {
            // Activity/页面重建但 ViewModel 仍存活时，复用已加载的章节。
            val saved = if (readingMemoryEnabled) readHistoryManager.lastReadPosition(readHistoryComicId, comicId) else ReaderPosition()
            onSuccess(saved)
            return@LaunchedEffect
        }
        if (localOnly) {
            comicReadViewModel.clearComicDetail()
            comicReadViewModel.getLocalComicPicList(
                comicId = comicId,
                context = context,
                restoreReadingMemory = readingMemoryEnabled,
                onSuccess = onSuccess,
            )
        } else {
            comicReadViewModel.loadRemoteComicChapter(
                comicId = comicId,
                shunt = localSettingManager.localSettingState.value.shunt,
                restoreReadingMemory = readingMemoryEnabled,
                onSuccess = onSuccess,
            )
        }
    }

    fun currentPosition(): ReaderPosition = if (localSettingManager.localSettingState.value.readMode == "scroll") {
        ReaderPosition(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset)
    } else ReaderPosition(pagerState.currentPage)

    val saveCheckpoint = rememberUpdatedState<(Boolean) -> Unit> { synchronous ->
        val state = comicReadViewModel.comicPicState.value
        val historyKey = comicReadViewModel.readHistoryComicId.intValue
        val position = progressSession.checkpoint(
            comicReadViewModel.loadedComicId.intValue,
            comicReadViewModel.size,
            currentPosition(),
        )
        val settings = localSettingManager.localSettingState.value
        if (settings.comicReadingMemoryEnabled && progressSession.readerMode == settings.readMode && !state.isLoading && historyKey > 0 && position != null) {
            readHistoryManager.saveReadProgress(
                historyKey, comicId, position.pageIndex, comicReadViewModel.size,
                scrollOffset = position.scrollOffset, synchronous = synchronous,
            )
        }
    }

    // 正常阅读时持续做检查点；滚动停止后保存偏移，后台时不依赖 onDispose。
    LaunchedEffect(comicId, localOnly, progressSession.restored, loading, size, localSetting.readMode, localSetting.comicReadingMemoryEnabled) {
        if (!progressSession.restored || loading || !localSetting.comicReadingMemoryEnabled) return@LaunchedEffect
        snapshotFlow { comicReadViewModel.readHistoryComicId.intValue to currentPosition() }
            .distinctUntilChanged()
            .debounce(if (localSetting.readMode == "scroll") 300L else 0L)
            .collect { saveCheckpoint.value(false) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, comicId, localOnly) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                saveCheckpoint.value(true)
                comicReadViewModel.trimReaderMemory()
            } else if (event == Lifecycle.Event.ON_RESUME && progressSession.restored) {
                if (localSettingManager.localSettingState.value.readMode == "scroll") {
                    val visible = lazyListState.layoutInfo.visibleItemsInfo
                    if (visible.isNotEmpty()) comicReadViewModel.decodeVisibleRange(visible.first().index, visible.last().index, context)
                } else comicReadViewModel.decodeIndex(pagerState.currentPage, context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            saveCheckpoint.value(true)
        }
    }

    // 页面真正定位完成后才允许写进度，避免第一个布局帧的 0 覆盖旧记录。
    LaunchedEffect(comicId, loading, size, loadedComicId, targetIndex, targetOffset, localSetting.readMode) {
        if (loading || size <= 0 || loadedComicId != comicId) {
            progressSession.startRestoring()
            return@LaunchedEffect
        }
        val visible = if (progressSession.readerMode == "scroll") {
            ReaderPosition(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset)
        } else ReaderPosition(pagerState.currentPage)
        val position = progressSession.targetForRestore(
            localSetting.readMode, ReaderPosition(targetIndex, targetOffset), visible,
        ).bounded(size)
        progressSession.startRestoring()
        targetIndex = position.pageIndex
        targetOffset = position.scrollOffset
        if (localSetting.readMode == "scroll") {
            snapshotFlow { lazyListState.layoutInfo.totalItemsCount }.first { it == size }
            lazyListState.scrollToItem(position.pageIndex)
            if (position.scrollOffset > 0) {
                // 长图占位高度与真实高度可能不同，等解码后再恢复图内偏移。
                val image = comicReadViewModel.comicPicState.value.data?.getOrNull(position.pageIndex)
                if (image != null) {
                    val decoded = snapshotFlow { image.imageResultState }
                        .first { it is ImageResultState.Success || it is ImageResultState.Failure }
                    if (decoded is ImageResultState.Success) {
                        val measured = snapshotFlow {
                            val layout = lazyListState.layoutInfo
                            layout.viewportSize.width to (layout.visibleItemsInfo.firstOrNull { it.index == position.pageIndex }?.size ?: 0)
                        }.first { (width, height) -> readerImageLayoutReady(width, height, decoded.decodeImageAspectRatio) }
                        lazyListState.scrollToItem(position.pageIndex, position.scrollOffset.coerceAtMost((measured.second - 1).coerceAtLeast(0)))
                    }
                }
            }
        } else pagerState.scrollToPage(position.pageIndex)
        currentIndexState = position.pageIndex
        progressSession.finishRestoring()
    }

    val view = LocalView.current
    val controller = remember(view) {
        val window = (context as? Activity)?.window
        if (window == null) {
            null
        } else {
            WindowInsetsControllerCompat(window, view).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
    LaunchedEffect(isShowToolbar) {
        if (isShowToolbar) {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller?.hide(WindowInsetsCompat.Type.statusBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (size <= 0) {
            Text(
                text = comicPicState.errorMsg.orEmpty().ifBlank { "暂无可阅读图片" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Box(Modifier.fillMaxSize().drawWithContent {
                drawContent()
                if (dimmingAlpha > 0f) drawRect(Color.Black.copy(alpha = dimmingAlpha))
            }) {
                if (localSetting.readMode == "scroll") {
                    ComicScrollRead(
                        lazyListState = lazyListState,
                        positionReady = progressSession.restored && progressSession.readerMode == localSetting.readMode,
                        zoomState = zoomState,
                        comicReadViewModel = comicReadViewModel,
                        onUpdateSliderValue = { updateIndexFromReader(it) }
                    )
                } else {
                    ComicPageRead(
                        pagerState = pagerState,
                        positionReady = progressSession.restored && progressSession.readerMode == localSetting.readMode,
                        zoomState = zoomState,
                        comicReadViewModel = comicReadViewModel,
                        tapOnly = localSetting.readMode == "tap",
                        onUpdateSliderValue = { updateIndexFromReader(it) }
                    )
                }
            }
            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp),
                visible = isShowToolbar,
                enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = tween(durationMillis = 250)
                ) + fadeIn(),
                exit = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = tween(durationMillis = 250)
                ) + fadeOut()
            ) {
                ReadSideBar(
                    comic = comic,
                    localOnly = localOnly,
                    chapterEnabled = readableChapters.isNotEmpty(),
                    onToggleCollect = {
                        if (!isLogin) {
                            mainNavController.navigate("login")
                        } else {
                            comic?.let { currentComic ->
                                if (currentComic.isCollect) {
                                    comicReadViewModel.unCollect(currentComic.id)
                                } else {
                                    comicReadViewModel.collect(currentComic.id)
                                }
                            }
                        }
                    },
                    onCache = {
                        comic?.let { currentComic ->
                            if (currentComic.comicChapterList.isEmpty()) {
                                downloadManager.downloadComic(currentComic)
                            } else {
                                selectedCacheChapterIds =
                                    currentComic.comicChapterList.map { it.id }.toSet()
                                activeDialog = ReadPanelDialog.Cache
                            }
                        }
                    },
                    onComment = {
                        if (!isLogin) {
                            mainNavController.navigate("login")
                        } else {
                            mainNavController.navigate("comment/$comicId")
                        }
                    },
                    onChapterJump = {
                        if (readableChapters.isNotEmpty()) {
                            activeDialog = ReadPanelDialog.Chapter
                        }
                    },
                )
            }
            AnimatedVisibility(
                modifier = Modifier.align(Alignment.BottomCenter),
                visible = isShowToolbar,
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 300)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 300)
                ) + fadeOut()
            ) {
                ToolsBar(
                    currentIndex = currentIndexState,
                    pageCount = size,
                    previousChapterEnabled = previousChapter != null,
                    nextChapterEnabled = nextChapter != null,
                    showResetZoom = zoomState.isZoomed,
                    onPreviousChapter = { navigateToChapter(previousChapter) },
                    onNextChapter = { navigateToChapter(nextChapter) },
                    onPageSelected = { jumpToIndex(it) },
                    onResetZoom = { zoomState.reset() },
                    brightnessPercent = brightnessPercent,
                    brightnessOverridden = brightnessOverride > 0,
                    onBrightnessChange = { brightnessOverride = it.coerceIn(1, 100) },
                    onRestoreBrightness = { brightnessOverride = 0 },
                )
            }
            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp),
                visible = isShowToolbar,
                enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = tween(durationMillis = 250)
                ) + fadeIn(),
                exit = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = tween(durationMillis = 250)
                ) + fadeOut()
            ) {
                ReaderExitButton(
                    onClick = { mainNavController.popBackStack() }
                )
            }
            if (localSetting.showComicPageReadTip && localSetting.readMode == "page" || localSetting.showComicScrollReadTip && localSetting.readMode == "scroll") {
                Tip(readMode = localSetting.readMode)
                TipCloseButton(
                    modifier = Modifier.align(
                        if (localSetting.readMode == "scroll") Alignment.CenterEnd else Alignment.BottomCenter
                    ).let {
                        if (localSetting.readMode == "scroll") {
                            it.padding(end = 40.dp)
                        } else {
                            it.padding(bottom = 40.dp)
                        }
                    },
                    onClick = {
                        if (localSetting.readMode == "scroll") {
                            localSettingManager.closeShowComicScrollReadTip()
                        } else {
                            localSettingManager.closeShowComicPageReadTip()
                        }
                    }
                )
            }
        }
    }

    when (activeDialog) {
        ReadPanelDialog.Cache -> {
            val currentComic = comic
            if (currentComic != null) {
                ChapterCachePickerDialog(
                    chapters = currentComic.comicChapterList,
                    selectedChapterIds = selectedCacheChapterIds,
                    onSelectedChange = { selectedCacheChapterIds = it },
                    onDismiss = { activeDialog = null },
                    onConfirm = {
                        val selectedChapters = currentComic.comicChapterList
                            .filter { it.id in selectedCacheChapterIds }
                        downloadManager.downloadChapters(currentComic, selectedChapters)
                        activeDialog = null
                        selectedCacheChapterIds = emptySet()
                    }
                )
            }
        }

        ReadPanelDialog.Chapter -> {
            if (readableChapters.isNotEmpty()) {
                ChapterPickerDialog(
                    title = "跳转章节",
                    chapters = readableChapters,
                    currentChapterId = comicId,
                    readChapterIds = readChapterIds,
                    onDismiss = { activeDialog = null },
                    onSelect = { chapter ->
                        activeDialog = null
                        navigateToChapter(chapter)
                    }
                )
            }
        }

        null -> Unit
    }
}

private enum class ReadPanelDialog {
    Cache,
    Chapter
}

@Composable
private fun ChapterCachePickerDialog(
    chapters: List<ComicChapter>,
    selectedChapterIds: Set<Int>,
    onSelectedChange: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val allChapterIds = remember(chapters) { chapters.map { it.id }.toSet() }
    val allSelected = chapters.isNotEmpty() && selectedChapterIds.containsAll(allChapterIds)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "选择缓存章节") },
        text = {
            if (chapters.isEmpty()) {
                Text(text = "暂无可选章节")
            } else {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectedChange(if (allSelected) emptySet() else allChapterIds)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { checked ->
                                onSelectedChange(if (checked) allChapterIds else emptySet())
                            }
                        )
                        Text(text = if (allSelected) "取消全选" else "全选")
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = adaptiveDialogMaxHeight())) {
                        itemsIndexed(chapters) { index, chapter ->
                            val selected = chapter.id in selectedChapterIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectedChange(
                                            if (selected) {
                                                selectedChapterIds - chapter.id
                                            } else {
                                                selectedChapterIds + chapter.id
                                            }
                                        )
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { checked ->
                                        onSelectedChange(
                                            if (checked) {
                                                selectedChapterIds + chapter.id
                                            } else {
                                                selectedChapterIds - chapter.id
                                            }
                                        )
                                    }
                                )
                                Text(
                                    text = chapter.name.ifBlank { "第 ${index + 1} 章" },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selectedChapterIds.isNotEmpty()
            ) {
                Text(text = "开始缓存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        }
    )
}

@Composable
private fun ReadSideBar(
    comic: Comic?,
    localOnly: Boolean,
    chapterEnabled: Boolean,
    onToggleCollect: () -> Unit,
    onCache: () -> Unit,
    onComment: () -> Unit,
    onChapterJump: () -> Unit,
) {
    Surface(
        modifier = Modifier.shadow(14.dp, RoundedCornerShape(28.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        tonalElevation = 10.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .width(82.dp)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ReadSideBarAction(
                icon = if (comic?.isCollect == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                label = "收藏",
                enabled = !localOnly && comic != null,
                onClick = onToggleCollect
            )
            ReadSideBarAction(
                icon = Icons.Default.Download,
                label = "缓存",
                enabled = !localOnly && comic != null,
                onClick = onCache
            )
            if (!localOnly) {
                ReadSideBarAction(
                    icon = Icons.AutoMirrored.Outlined.Message,
                    label = "评论",
                    enabled = comic != null,
                    onClick = onComment
                )
            }
            ReadChapterSideBarAction(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                label = "章节",
                enabled = chapterEnabled,
                onClick = onChapterJump
            )
        }
    }
}

@Composable
private fun ReaderExitButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "退出阅读"
            )
        }
    }
}

@Composable
private fun ReadSideBarAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = contentColor
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReadChapterSideBarAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 7.dp, vertical = 4.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        tonalElevation = if (enabled) 3.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(23.dp),
                tint = contentColor
            )
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChapterPickerDialog(
    title: String,
    chapters: List<ComicChapter>,
    currentChapterId: Int?,
    readChapterIds: Set<Int>,
    onDismiss: () -> Unit,
    onSelect: (ComicChapter) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentIndex = remember(chapters, currentChapterId) {
        chapters.indexOfFirst { it.id == currentChapterId }
    }
    LaunchedEffect(currentIndex, chapters.size) {
        if (currentIndex >= 0) {
            listState.scrollToItem(currentIndex)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            if (chapters.isEmpty()) {
                Text(text = "暂无可选章节")
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = adaptiveDialogMaxHeight())
                ) {
                    itemsIndexed(chapters) { index, chapter ->
                        val selected = chapter.id == currentChapterId
                        val read = chapter.id in readChapterIds
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(chapter) }
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else if (read) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            contentColor = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else if (read) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            tonalElevation = if (selected) 3.dp else 0.dp
                        ) {
                            Text(
                                text = chapter.name.ifBlank { "第 ${index + 1} 章" },
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        }
    )
}

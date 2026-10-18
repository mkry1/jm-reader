package com.par9uet.jm.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.par9uet.jm.personal.PersonalBackup
import com.par9uet.jm.personal.PersonalLibrary
import com.par9uet.jm.personal.PersonalBackupManager
import com.par9uet.jm.personal.decodePersonalBackup
import com.par9uet.jm.personal.readBackupText
import com.par9uet.jm.store.LocalSettingManager
import com.par9uet.jm.ui.components.Comic
import com.par9uet.jm.ui.components.CommonScaffold
import com.par9uet.jm.ui.components.adaptiveComicGridCells
import com.par9uet.jm.ui.viewModel.ComicViewModel
import com.par9uet.jm.ui.viewModel.PersonalRecommendationViewModel
import com.par9uet.jm.utils.filterBlockedTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
fun PersonalLibraryScreen(
    library: PersonalLibrary = getKoin().get(),
    settingsManager: LocalSettingManager = getKoin().get(),
    backups: PersonalBackupManager = getKoin().get(),
    home: ComicViewModel = koinActivityViewModel(),
    recommendations: PersonalRecommendationViewModel = koinActivityViewModel(),
) {
    val records by library.records.collectAsState()
    val homeState by home.homeComicState.collectAsState()
    val state by recommendations.state.collectAsState()
    val settings by settingsManager.localSettingState.collectAsState()
    val likes = remember(records) { records.values.filter { it.liked && !it.dismissed } }
    var selectedTab by rememberSaveable { mutableStateOf("为你推荐") }
    var message by remember { mutableStateOf("") }
    var pendingBackup by remember { mutableStateOf<PersonalBackup?>(null) }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navigation = LocalMainNavController.current

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val snapshot = backups.snapshot()
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "无法写入备份文件" }
                        .bufferedWriter(Charsets.UTF_8).use { it.write(snapshot) }
                }
                message = "备份已保存，包含阅读记录和搜索浏览进度"
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { message = "备份保存失败，请重新选择位置"
            } finally { busy = false }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                pendingBackup = withContext(Dispatchers.IO) {
                    val text = requireNotNull(context.contentResolver.openInputStream(uri)) { "无法打开备份" }
                        .bufferedReader(Charsets.UTF_8).use { reader ->
                            readBackupText(reader)
                        }
                    decodePersonalBackup(text)
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { message = e.message ?: "备份无法读取，现有记录未改变"
            } finally { busy = false }
        }
    }

    LaunchedEffect(likes, settings.comicApiSource, settings.api) {
        home.getHomeComic(forceRefresh = false)
        recommendations.refresh(likes)
    }
    pendingBackup?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingBackup = null },
            title = { Text("合并阅读记录") },
            text = { Text("导入 ${backup.records.size} 部作品的状态、${backup.readHistory.size} 条阅读进度和 ${backup.searchBrowseHistory.size} 条搜索进度。作品状态和搜索位置保留较新的记录；本机已有的阅读进度优先。") },
            confirmButton = {
                TextButton(onClick = {
                    backups.restore(backup)
                    pendingBackup = null
                    message = "阅读记录已合并"
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { pendingBackup = null }) { Text("取消") } },
        )
    }

    val visibleComics = remember(records, selectedTab, state.comics, homeState.list, settings.blockedTagList, settings.homeExcludedTags) {
        if (selectedTab == "为你推荐") {
            library.recommend(state.comics + homeState.list.flatMap { it.list })
                .filterBlockedTags((settings.blockedTagList + settings.homeExcludedTags).distinct())
        } else {
            records.values.filter {
                when (selectedTab) {
                    "已读" -> it.read
                    "喜欢" -> it.liked
                    else -> it.dismissed
                }
            }.sortedByDescending { it.updatedAt }.map { it.comic }
        }
    }
    CommonScaffold(title = "我的阅读与推荐") {
        Column(Modifier.fillMaxSize()) {
            FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (tab in listOf("为你推荐", "已读", "喜欢", "不感兴趣")) {
                    FilterChip(selected = selectedTab == tab, onClick = { selectedTab = tab }, label = { Text(tab) })
                }
            }
            FlowRow(Modifier.padding(horizontal = 8.dp)) {
                TextButton(enabled = !busy, onClick = { navigation.navigate("cloudBackup") }) { Text("GitHub 云备份") }
                TextButton(enabled = !busy, onClick = { export.launch("jm-reading-backup.json") }) { Text("导出备份") }
                TextButton(enabled = !busy, onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("导入备份") }
                TextButton(enabled = !state.loading, onClick = { home.getHomeComic(); recommendations.refresh(likes) }) { Text("刷新推荐") }
            }
            Text(
                if (selectedTab == "为你推荐") "根据你喜欢的作者和标签排序，自动排除已读和不感兴趣。长按封面可标记。"
                else "长按封面可以撤销或修改状态；阅读记录保存在本机。",
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
            )
            if (message.isNotBlank()) Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            if (busy || (selectedTab == "为你推荐" && (state.loading || homeState.isLoading))) {
                CircularProgressIndicator(Modifier.padding(12.dp))
            }
            LazyVerticalGrid(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                columns = adaptiveComicGridCells(), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (visibleComics.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (selectedTab == "为你推荐") state.message.ifBlank { homeState.errorMsg?.takeIf { homeState.isError } ?: "暂无可推荐作品，可刷新或先标记几部喜欢的漫画" }
                        else "还没有记录。可在漫画详情页或长按封面添加。",
                        Modifier.padding(16.dp),
                    )
                }
                if (selectedTab == "为你推荐" && state.message.isNotBlank() && visibleComics.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Text(state.message, style = MaterialTheme.typography.bodySmall) }
                }
                items(visibleComics, key = { it.id }) { Comic(it) }
            }
        }
    }
}

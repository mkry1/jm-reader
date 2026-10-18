package com.par9uet.jm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.par9uet.jm.data.models.Comic
import com.par9uet.jm.personal.PersonalLibrary
import com.par9uet.jm.personal.personalId
import org.koin.compose.getKoin

@Composable
fun PersonalComicActions(comic: Comic, library: PersonalLibrary = getKoin().get()) {
    val records by library.records.collectAsState()
    val record = records[comic.personalId()]
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = record?.read == true,
            onClick = { library.toggleRead(comic) },
            label = { Text(if (record?.read == true) "已读 · 点击撤销" else "标记已读") },
        )
        FilterChip(
            selected = record?.liked == true,
            onClick = { library.toggleLiked(comic) },
            label = { Text(if (record?.liked == true) "已喜欢" else "喜欢") },
        )
        FilterChip(
            selected = record?.dismissed == true,
            onClick = { library.toggleDismissed(comic) },
            label = { Text(if (record?.dismissed == true) "已屏蔽 · 点击撤销" else "不感兴趣") },
        )
    }
}

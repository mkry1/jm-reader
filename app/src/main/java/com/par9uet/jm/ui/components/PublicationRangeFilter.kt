package com.par9uet.jm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.par9uet.jm.search.PublicationRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicationRangeFilter(range: PublicationRange?, onChange: (PublicationRange?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
    val presets = listOf(7, 30, 90).associateWith { PublicationRange(today.minusDays(it - 1L).toString(), today.toString()) }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text("上架时间", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = range == null, onClick = { onChange(null) }, label = { Text("不限") })
            presets.forEach { (days, preset) ->
                FilterChip(selected = range == preset, onClick = { onChange(preset) }, label = { Text("最近${days}天") })
            }
            FilterChip(selected = range != null && range !in presets.values, onClick = { showPicker = true }, label = { Text("自选日期") })
        }
        if (range != null) Text("${range.from} 至 ${range.until} · 按最新上架排序", style = MaterialTheme.typography.bodySmall)
    }
    if (showPicker) {
        val picker = rememberDateRangePickerState(
            initialSelectedStartDateMillis = range?.from?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
            initialSelectedEndDateMillis = range?.until?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(enabled = picker.selectedStartDateMillis != null && picker.selectedEndDateMillis != null, onClick = {
                    val start = Instant.ofEpochMilli(requireNotNull(picker.selectedStartDateMillis)).atZone(ZoneOffset.UTC).toLocalDate()
                    val end = Instant.ofEpochMilli(requireNotNull(picker.selectedEndDateMillis)).atZone(ZoneOffset.UTC).toLocalDate()
                    onChange(PublicationRange(start.toString(), end.toString()))
                    showPicker = false
                }) { Text("应用") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
        ) {
            DateRangePicker(state = picker, modifier = Modifier.heightIn(max = 480.dp), showModeToggle = true, title = { Text("选择上架日期范围", Modifier.padding(16.dp)) })
        }
    }
}

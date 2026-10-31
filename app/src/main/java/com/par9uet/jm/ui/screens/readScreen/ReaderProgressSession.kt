package com.par9uet.jm.ui.screens.readScreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.par9uet.jm.storage.ReaderPosition

/** 未完成定位时，第 0 页只是界面初始值，不能覆盖真正的阅读进度。 */
class ReaderProgressSession(private val chapterId: Int) {
    var readerMode by mutableStateOf<String?>(null)
        private set
    var restored by mutableStateOf(false)
        private set

    fun startRestoring() { restored = false }
    fun finishRestoring() { restored = true }

    fun targetForRestore(mode: String, requested: ReaderPosition, visible: ReaderPosition): ReaderPosition {
        val modeChanged = readerMode != null && readerMode != mode
        val target = if (modeChanged && restored) visible else requested
        readerMode = mode
        return if (mode == "scroll") target else target.copy(scrollOffset = 0)
    }

    fun checkpoint(loadedChapterId: Int, pageCount: Int, position: ReaderPosition): ReaderPosition? =
        position.bounded(pageCount).takeIf { restored && loadedChapterId == chapterId && pageCount > 0 }
}

fun readerImageLayoutReady(viewportWidth: Int, itemHeight: Int, aspectRatio: Float): Boolean =
    viewportWidth > 0 && aspectRatio.isFinite() && aspectRatio > 0f &&
        kotlin.math.abs(itemHeight - viewportWidth / aspectRatio) <= 1f

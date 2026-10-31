package com.par9uet.jm.storage

data class ReaderPosition(val pageIndex: Int = 0, val scrollOffset: Int = 0) {
    fun bounded(pageCount: Int): ReaderPosition = copy(
        pageIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        scrollOffset = scrollOffset.coerceAtLeast(0),
    )
}

package com.par9uet.jm.storage

data class ComicReadHistory(
    val lastChapterId: Int = 0,
    val readChapterIds: List<Int> = emptyList(),
    val lastPageIndex: Int = 0,
    val lastChapterPageCount: Int = 0,
    val lastPageScrollOffset: Int = 0,
)

package com.par9uet.jm.ui.screens.readScreen

import org.junit.Assert.*
import org.junit.Test

class ReaderMemoryWindowTest {
    @Test fun readingAnEntireChapterKeepsOnlyNearbyDecodedPages() {
        val cache = ReaderMemoryWindow()
        val livePages = mutableSetOf<Int>()
        for (page in 0 until 1000) {
            cache.retain(1000, page, page, 3)
            for (index in cache.retainedRange) {
                if (!cache.contains(index)) {
                    livePages.add(index)
                    cache.register(index) { livePages.remove(index) }
                }
            }
            assertTrue(livePages.size <= 7)
            assertTrue(page in livePages)
        }
        cache.clear()
        assertTrue(livePages.isEmpty())
    }

    @Test fun trimmingForBackgroundPreservesAllVisiblePages() {
        val cache = ReaderMemoryWindow()
        val released = mutableSetOf<Int>()
        cache.retain(100, 10, 12, 3)
        for (index in cache.retainedRange) cache.register(index) { released.add(index) }
        cache.retain(100, 10, 12, 0)
        assertEquals(setOf(7, 8, 9, 13, 14, 15), released)
        for (index in 10..12) assertTrue(cache.contains(index))
    }
}

package com.par9uet.jm.store

import com.par9uet.jm.storage.ComicReadHistory
import com.par9uet.jm.storage.ReaderPosition
import com.par9uet.jm.ui.screens.readScreen.ReaderProgressSession
import org.junit.Assert.*
import org.junit.Test

class ReaderRecoveryTest {
    @Test fun coldReaderCanRestoreBeforeNetworkInitialization() {
        val saved = mapOf(100 to ComicReadHistory(lastChapterId = 101, lastPageIndex = 17, lastChapterPageCount = 80, lastPageScrollOffset = 345))
        val history = ReadHistoryManager({ saved }, { _, _ -> })
        val key = history.historyKeyForChapter(101)
        assertEquals(100, key)
        assertEquals(ReaderPosition(17, 345), history.lastReadPosition(key, 101))
    }

    @Test fun backgroundCheckpointSurvivesANewReaderWithoutDisposal() {
        var disk = emptyMap<Int, ComicReadHistory>()
        var flushed = false
        val history = ReadHistoryManager({ disk }, { value, synchronous -> disk = value; flushed = synchronous })
        history.saveReadProgress(100, 101, 23, 80, scrollOffset = 270, synchronous = true)
        assertTrue(flushed)
        val restarted = ReadHistoryManager({ disk }, { _, _ -> })
        assertEquals(ReaderPosition(23, 270), restarted.lastReadPosition(100, 101))
    }

    @Test fun openingANewChapterDoesNotInheritThePreviousChapterPosition() {
        val saved = mapOf(100 to ComicReadHistory(lastChapterId = 101, readChapterIds = listOf(101), lastPageIndex = 17, lastChapterPageCount = 80))
        val history = ReadHistoryManager({ saved }, { _, _ -> })
        history.markRead(100, 102)
        assertEquals(ReaderPosition(), history.lastReadPosition(100, 102))
        assertEquals(setOf(101, 102), history.readChapterIds(100))
    }

    @Test fun lateMetadataForTheSameChapterPreservesItsCheckpoint() {
        val saved = mapOf(100 to ComicReadHistory(lastChapterId = 101, lastPageIndex = 17, lastChapterPageCount = 80, lastPageScrollOffset = 345))
        val history = ReadHistoryManager({ saved }, { _, _ -> })
        history.markRead(100, 101)
        assertEquals(ReaderPosition(17, 345), history.lastReadPosition(100, 101))
    }

    @Test fun initialZeroPageCannotOverwriteProgressDuringRestoration() {
        val session = ReaderProgressSession(101)
        assertNull(session.checkpoint(101, 80, ReaderPosition()))
        session.finishRestoring()
        assertEquals(ReaderPosition(17, 345), session.checkpoint(101, 80, ReaderPosition(17, 345)))
        assertNull(session.checkpoint(102, 80, ReaderPosition()))
        session.startRestoring()
        assertNull(session.checkpoint(101, 80, ReaderPosition()))
    }

    @Test fun changingReaderModeUsesVisiblePageInsteadOfTheOldRestoreTarget() {
        val session = ReaderProgressSession(101)
        val oldTarget = ReaderPosition(17)
        assertEquals(oldTarget, session.targetForRestore("page", oldTarget, ReaderPosition()))
        session.finishRestoring()
        assertEquals(ReaderPosition(23), session.targetForRestore("scroll", oldTarget, ReaderPosition(23)))
        assertEquals(ReaderPosition(25), session.targetForRestore("tap", oldTarget, ReaderPosition(25, 350)))
    }

    @Test fun longPageOffsetWaitsForTheDecodedImageLayout() {
        assertFalse(com.par9uet.jm.ui.screens.readScreen.readerImageLayoutReady(1080, 1920, 0.2f))
        assertTrue(com.par9uet.jm.ui.screens.readScreen.readerImageLayoutReady(1080, 5400, 0.2f))
    }
}

package com.par9uet.jm.personal

import com.par9uet.jm.data.models.Comic
import com.par9uet.jm.storage.ComicReadHistory
import org.junit.Assert.*
import org.junit.Test

class PersonalLibraryTest {
    private fun comic(id: Int, author: String = "", tags: List<String> = emptyList()) =
        Comic.create(id, "作品$id", listOf(author)).copy(tagList = tags)

    @Test fun readAndDismissedComicsAreExcludedWhileLikedUnreadComicsRemain() {
        val library = PersonalLibrary({ emptyMap() }, {})
        library.toggleRead(comic(1))
        library.toggleDismissed(comic(2))
        library.toggleLiked(comic(3))
        assertEquals(listOf(3, 4), library.recommend(listOf(comic(1), comic(2), comic(3), comic(4))).map { it.id })
        library.toggleRead(comic(1))
        assertTrue(library.recommend(listOf(comic(1))).isNotEmpty())
    }

    @Test fun readingProgressAloneDoesNotMarkAComicAsFinished() {
        val progress = mapOf(1 to ComicReadHistory(lastChapterId = 1, lastPageIndex = 12, lastChapterPageCount = 20))
        val library = PersonalLibrary({ emptyMap() }, {})
        val restored = decodePersonalBackup(library.exportBackup(progress))
        assertEquals(12, restored.readHistory.getValue(1).lastPageIndex)
        assertEquals(listOf(1), library.recommend(listOf(comic(1))).map { it.id })
    }

    @Test fun preferencesPersistAndRestoreWithoutLosingExistingRecords() {
        var saved = emptyMap<Int, PersonalComic>()
        val library = PersonalLibrary({ saved }, { saved = it })
        library.toggleRead(comic(1))
        library.toggleLiked(comic(2, "作者甲", listOf("标签甲")))
        val backup = decodePersonalBackup(library.exportBackup(emptyMap()))
        val other = PersonalLibrary({ emptyMap() }, {})
        other.toggleDismissed(comic(3))
        other.restore(backup)
        assertEquals(setOf(1, 2, 3), other.records.value.keys)
        assertTrue(other.records.value.getValue(1).read)
        assertTrue(other.records.value.getValue(2).liked)
        assertTrue(other.records.value.getValue(3).dismissed)
        assertTrue(PersonalLibrary({ saved }, {}).records.value.getValue(1).read)
    }

    @Test fun matchingAuthorsAndTagsRankAheadOfUnrelatedComicsAndDuplicatesAreRemoved() {
        val library = PersonalLibrary({ emptyMap() }, {})
        library.toggleLiked(comic(1, "作者甲", listOf("标签甲")))
        library.toggleRead(comic(1))
        val results = library.recommend(listOf(comic(2), comic(3, tags = listOf("标签甲")), comic(4, "作者甲"), comic(4)))
        assertEquals(listOf(4, 3, 2), results.map { it.id })
    }

    @Test fun chaptersOfAnExcludedSeriesAreAlsoExcluded() {
        val library = PersonalLibrary({ emptyMap() }, {})
        library.toggleRead(comic(100))
        assertTrue(library.recommend(listOf(comic(101).copy(seriesId = "100"))).isEmpty())
    }

    @Test fun invalidBackupCannotBeImportedAsAnEmptyLibrary() {
        for (json in listOf("{}", "{\"format\":\"other-app\",\"version\":1}", "{\"format\":\"jm-personal-library\",\"version\":99}")) {
            assertThrows(IllegalArgumentException::class.java) { decodePersonalBackup(json) }
        }
    }

    @Test fun nullProgressAndNullComicFieldsAreRejectedBeforeImport() {
        val nullProgress = """{"format":"jm-personal-library","version":1,"records":{},"readHistory":{"999":null}}"""
        assertThrows(IllegalArgumentException::class.java) { decodePersonalBackup(nullProgress) }
        val library = PersonalLibrary({ emptyMap() }, {})
        library.toggleLiked(comic(1))
        for (field in listOf("tagList", "coverUrl", "name")) {
            val root = com.google.gson.JsonParser.parseString(library.exportBackup(emptyMap())).asJsonObject
            root.getAsJsonObject("records").getAsJsonObject("1").getAsJsonObject("comic")
                .add(field, com.google.gson.JsonNull.INSTANCE)
            assertThrows(IllegalArgumentException::class.java) { decodePersonalBackup(root.toString()) }
        }
    }

    @Test fun restoringOlderBackupDoesNotUndoNewerLocalStatus() {
        val library = PersonalLibrary({ emptyMap() }, {})
        library.toggleRead(comic(1))
        val incoming = PersonalBackup(records = mapOf(1 to PersonalComic(comic(1), read = false, updatedAt = 1)))
        library.restore(incoming)
        assertTrue(library.records.value.getValue(1).read)
    }
}

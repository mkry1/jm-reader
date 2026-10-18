package com.par9uet.jm.personal

import com.par9uet.jm.search.SearchBrowseHistory
import com.par9uet.jm.storage.SecureStorage
import com.par9uet.jm.store.ReadHistoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PersonalBackupManager(
    private val library: PersonalLibrary,
    private val history: ReadHistoryManager,
    private val searches: SearchBrowseHistory,
) {
    fun snapshot(): String = library.exportBackup(history.readHistoryState.value, searches.records.value)

    fun restore(backup: PersonalBackup) {
        library.restore(backup)
        history.restoreReadHistory(backup.readHistory)
        searches.restore(backup.searchBrowseHistory)
    }
}

class GitHubBackupStore(private val storage: SecureStorage) {
    private val key = "githubBackupSettings"
    private val mutableSettings = MutableStateFlow(
        storage.get<GitHubBackupSettings>(key, GitHubBackupSettings::class.java) ?: GitHubBackupSettings(),
    )
    val settings = mutableSettings.asStateFlow()

    fun save(value: GitHubBackupSettings) {
        val validated = value.validated()
        val old = settings.value
        val next = validated.copy(lastUploadedAt = if (validated.owner == old.owner && validated.repository == old.repository) old.lastUploadedAt else 0)
        persist(next)
    }

    fun markUploaded() = persist(settings.value.copy(lastUploadedAt = System.currentTimeMillis()))

    fun clear() {
        storage.remove(key)
        mutableSettings.value = GitHubBackupSettings()
    }

    private fun persist(value: GitHubBackupSettings) {
        storage.setEncrypted(key, value)
        mutableSettings.value = value
    }
}

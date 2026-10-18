package com.par9uet.jm.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.par9uet.jm.personal.GitHubBackupClient
import com.par9uet.jm.personal.GitHubBackupSettings
import com.par9uet.jm.personal.GitHubBackupStore
import com.par9uet.jm.personal.PersonalBackup
import com.par9uet.jm.personal.PersonalBackupManager
import com.par9uet.jm.ui.components.CommonScaffold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.getKoin
import java.text.DateFormat
import java.util.Date

@Composable
fun CloudBackupScreen(
    backups: PersonalBackupManager = getKoin().get(),
    store: GitHubBackupStore = getKoin().get(),
    client: GitHubBackupClient = getKoin().get(),
) {
    val settings by store.settings.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<PersonalBackup?>(null) }
    val scope = rememberCoroutineScope()

    if (showSettings) GitHubBackupSettingsDialog(settings, store, onDismiss = { showSettings = false })
    pending?.let { backup ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("恢复云端备份") },
            text = { Text("合并 ${backup.records.size} 部作品的状态、${backup.readHistory.size} 条阅读进度和 ${backup.searchBrowseHistory.size} 条搜索进度。作品状态和搜索位置保留较新的记录；阅读进度优先保留本机已有记录。") },
            confirmButton = {
                TextButton(onClick = {
                    backups.restore(backup)
                    pending = null
                    message = "云端备份已恢复"
                }) { Text("恢复") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } },
        )
    }

    CommonScaffold(title = "GitHub 云备份") {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("备份到你的 GitHub 私有仓库", style = MaterialTheme.typography.titleLarge)
            Text("保存已读、喜欢、不感兴趣、阅读章节和页数，以及搜索页码和滚动位置。点击按钮时才会上传或读取。")
            Text("备份仓库：${settings.owner}/${settings.repository}")
            Text(if (settings.token.isBlank()) "请先配置云备份访问凭据" else "已保存本机访问凭据")
            if (settings.lastUploadedAt > 0) {
                Text("上次成功上传：${DateFormat.getDateTimeInstance().format(Date(settings.lastUploadedAt))}")
            }
            OutlinedButton(enabled = !busy, onClick = { showSettings = true }, modifier = Modifier.fillMaxWidth()) {
                Text("云备份设置")
            }
            Button(
                enabled = !busy,
                onClick = {
                    if (settings.token.isBlank()) showSettings = true else {
                        val config = settings
                        val snapshot = backups.snapshot()
                        busy = true
                        message = "正在上传备份…"
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { client.upload(config, snapshot) }
                                store.markUploaded()
                                message = "已上传到 GitHub 私有仓库，可在重装或换手机后恢复"
                            } catch (e: CancellationException) { throw e
                            } catch (e: Exception) { message = e.message ?: "备份上传失败，请检查网络后重试"
                            } finally { busy = false }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("上传备份") }
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    if (settings.token.isBlank()) showSettings = true else {
                        val config = settings
                        busy = true
                        message = "正在读取云端备份…"
                        scope.launch {
                            try {
                                pending = withContext(Dispatchers.IO) { client.download(config) }
                                message = "备份已读取，请确认是否恢复"
                            } catch (e: CancellationException) { throw e
                            } catch (e: Exception) { message = e.message ?: "备份读取失败，请检查网络后重试"
                            } finally { busy = false }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("恢复备份") }
            if (busy) CircularProgressIndicator()
            if (message.isNotBlank()) Text(message)
            Text("访问凭据仅加密保存在这台手机，不包含在备份文件中。卸载后需要重新配置凭据，再恢复云端记录。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GitHubBackupSettingsDialog(
    settings: GitHubBackupSettings,
    store: GitHubBackupStore,
    onDismiss: () -> Unit,
) {
    var owner by remember { mutableStateOf(settings.owner) }
    var repository by remember { mutableStateOf(settings.repository) }
    var token by remember { mutableStateOf(settings.token) }
    var error by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("云备份设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(owner, { owner = it }, label = { Text("仓库所有者") }, singleLine = true)
                OutlinedTextField(repository, { repository = it }, label = { Text("私有仓库名称") }, singleLine = true)
                Text("首次配置：创建访问凭据，只选择备份仓库；Contents 权限选 Read and write。复制生成的凭据，粘贴到下方。")
                TextButton(onClick = {
                    uriHandler.openUri("https://github.com/settings/personal-access-tokens/new?name=JM-Reader-backup&target_name=${Uri.encode(owner.trim())}&contents=write&expires_in=90")
                }) { Text("前往 GitHub 创建访问凭据") }
                OutlinedTextField(
                    token, { token = it }, label = { Text("访问凭据（Fine-grained token）") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                )
                Text("Repository access 选择 Only select repositories → $repository。凭据过期后在这里更换即可。", style = MaterialTheme.typography.bodySmall)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                if (settings.token.isNotBlank()) TextButton(onClick = { store.clear(); onDismiss() }) { Text("移除本机保存的凭据") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    store.save(GitHubBackupSettings(owner, repository, token))
                    onDismiss()
                } catch (e: IllegalArgumentException) { error = e.message.orEmpty() }
            }) { Text("保存设置") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

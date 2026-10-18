package com.par9uet.jm.personal

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.toByteString
import java.io.Reader

data class GitHubBackupSettings(
    val owner: String = "mkry1",
    val repository: String = "jm-reader-backup",
    val token: String = "",
    val lastUploadedAt: Long = 0,
) {
    fun validated(): GitHubBackupSettings {
        val result = copy(owner = owner.trim(), repository = repository.trim(), token = token.trim())
        require(result.owner.matches(Regex("[A-Za-z0-9-]+")) && result.repository.matches(Regex("[A-Za-z0-9_.-]+"))) { "请填写正确的仓库所有者和名称" }
        require(result.token.isNotBlank()) { "请先配置 GitHub 访问凭据" }
        return result
    }
    override fun toString(): String = "$owner/$repository"
}

/** 使用 GitHub Contents API。只接受私有仓库，凭据不进入备份内容。 */
class GitHubBackupClient(
    private val client: OkHttpClient,
    private val api: HttpUrl = "https://api.github.com/".toHttpUrl(),
) {
    fun upload(settings: GitHubBackupSettings, snapshot: String) {
        decodePersonalBackup(snapshot)
        val config = settings.validated()
        checkPrivateRepository(config)
        val url = contentsUrl(config)
        val sha = client.newCall(request(config, url).build()).execute().use { response ->
            if (response.code == 404) null else {
                checkResponse(response.code)
                JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject.get("sha")?.asString
                    ?: error("无法读取云端备份版本，未执行覆盖")
            }
        }
        val body = JsonObject().apply {
            addProperty("message", "Update reading backup")
            addProperty("content", snapshot.toByteArray(Charsets.UTF_8).toByteString().base64())
            sha?.let { addProperty("sha", it) }
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        client.newCall(request(config, url).put(body).build()).execute().use { checkResponse(it.code) }
    }

    fun download(settings: GitHubBackupSettings): PersonalBackup {
        val config = settings.validated()
        checkPrivateRepository(config)
        val request = request(config, contentsUrl(config))
            .header("Accept", "application/vnd.github.raw+json").build()
        return client.newCall(request).execute().use { response ->
            if (response.code == 404) throw IllegalArgumentException("云端还没有备份，请先在原手机上传")
            checkResponse(response.code)
            val body = requireNotNull(response.body) { "云端备份为空" }
            body.charStream().use { decodePersonalBackup(readBackupText(it)) }
        }
    }

    private fun checkPrivateRepository(settings: GitHubBackupSettings) {
        val url = api.newBuilder().addPathSegments("repos").addPathSegment(settings.owner).addPathSegment(settings.repository).build()
        client.newCall(request(settings, url).build()).execute().use { response ->
            checkResponse(response.code)
            val repository = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
            require(repository.get("private")?.asBoolean == true) { "请选择私有仓库，个人阅读记录不会上传到公开仓库" }
        }
    }

    private fun contentsUrl(settings: GitHubBackupSettings): HttpUrl = api.newBuilder()
        .addPathSegments("repos").addPathSegment(settings.owner).addPathSegment(settings.repository)
        .addPathSegments("contents/reading-backup.json").build()

    private fun request(settings: GitHubBackupSettings, url: HttpUrl): Request.Builder = Request.Builder()
        .url(url).header("Authorization", "Bearer ${settings.token}")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "jm-reader-backup")

    private fun checkResponse(status: Int) {
        require(status in 200..299) {
            when (status) {
                401 -> "GitHub 访问凭据无效或已过期，请重新配置"
                403 -> "GitHub 拒绝访问，请检查凭据的 Contents 读写权限或稍后重试"
                404 -> "找不到备份仓库，请检查仓库名称及凭据授权范围"
                409 -> "云端备份已发生变化，本次未覆盖，请重新上传"
                else -> "GitHub 请求失败（$status），请稍后重试"
            }
        }
    }
}

fun readBackupText(reader: Reader): String {
    val chars = CharArray(16 * 1024 * 1024 + 1)
    var count = 0
    while (count < chars.size) {
        val size = reader.read(chars, count, chars.size - count)
        if (size < 0) break
        count += size
    }
    require(count < chars.size) { "备份文件过大" }
    return String(chars, 0, count)
}

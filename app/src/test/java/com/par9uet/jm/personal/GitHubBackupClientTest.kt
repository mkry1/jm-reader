package com.par9uet.jm.personal

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.Base64

class GitHubBackupClientTest {
    private val settings = GitHubBackupSettings(token = "test-token")
    private val snapshot = """{"format":"jm-personal-library","version":1,"records":{},"readHistory":{}}"""

    private class FakeGitHub : AutoCloseable {
        var privateRepo = true
        var repositoryStatus = 200
        var conflict = false
        var content: String? = null
        var uploads = 0
        var authorized = true
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                authorized = authorized && exchange.requestHeaders.getFirst("Authorization") == "Bearer test-token"
                val isRepository = exchange.requestURI.path == "/repos/mkry1/jm-reader-backup"
                var status = 200
                val body = when {
                    isRepository -> {
                        status = repositoryStatus
                        """{"private":$privateRepo}"""
                    }
                    exchange.requestMethod == "PUT" -> {
                        val json = JsonParser.parseString(exchange.requestBody.reader().readText()).asJsonObject
                        if (content != null) assertEquals("backup-sha", json.get("sha").asString)
                        if (conflict) { status = 409; "{}" } else {
                            content = String(Base64.getDecoder().decode(json.get("content").asString), Charsets.UTF_8)
                            uploads++
                            status = 201
                            "{}"
                        }
                    }
                    content == null -> { status = 404; "{}" }
                    exchange.requestHeaders.getFirst("Accept").contains("raw") -> content!!
                    else -> """{"sha":"backup-sha"}"""
                }
                val bytes = body.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        fun client() = GitHubBackupClient(OkHttpClient(), "http://127.0.0.1:${server.address.port}/".toHttpUrl())
        override fun close() = server.stop(0)
    }

    @Test fun createsUpdatesAndDownloadsPrivateBackup() {
        FakeGitHub().use { github ->
            val client = github.client()
            client.upload(settings, snapshot)
            assertEquals(snapshot, github.content)
            client.upload(settings, snapshot)
            assertEquals(2, github.uploads)
            assertEquals(1, client.download(settings).version)
            assertTrue(github.authorized)
        }
    }

    @Test fun refusesPublicRepositoryBeforeUploadingPersonalData() {
        FakeGitHub().use { github ->
            github.privateRepo = false
            assertThrows(IllegalArgumentException::class.java) { github.client().upload(settings, snapshot) }
            assertNull(github.content)
            assertEquals(0, github.uploads)
        }
    }

    @Test fun reportsExpiredCredentialsAndMissingBackup() {
        FakeGitHub().use { github ->
            github.repositoryStatus = 401
            val error = assertThrows(IllegalArgumentException::class.java) { github.client().upload(settings, snapshot) }
            assertTrue(error.message!!.contains("凭据"))
            github.repositoryStatus = 200
            val missing = assertThrows(IllegalArgumentException::class.java) { github.client().download(settings) }
            assertTrue(missing.message!!.contains("备份"))
        }
    }

    @Test fun conflictDoesNotOverwriteCloudBackup() {
        FakeGitHub().use { github ->
            github.content = snapshot
            github.conflict = true
            assertThrows(IllegalArgumentException::class.java) { github.client().upload(settings, snapshot) }
            assertEquals(0, github.uploads)
            assertEquals(snapshot, github.content)
        }
    }
}

package de.circuitcurios.copyboard

import android.util.Base64
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

data class GitHubSyncConfig(
    val owner: String,
    val repo: String,
    val branch: String,
    val path: String,
    val token: String
) {
    fun isComplete(): Boolean = owner.isNotBlank() && repo.isNotBlank() && branch.isNotBlank() && path.isNotBlank() && token.isNotBlank()
}

class GitHubSyncClient(private val config: GitHubSyncConfig) {
    fun load(): String {
        val response = request(
            method = "GET",
            url = contentsUrl(withRef = true),
            body = null
        )
        val json = JSONObject(response)
        val encoded = json.getString("content").replace("\n", "")
        return String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8)
    }

    fun save(content: String) {
        val sha = currentShaOrNull()
        val encoded = Base64.encodeToString(content.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val payload = JSONObject()
            .put("message", "Sync Copyboard snippets")
            .put("content", encoded)
            .put("branch", config.branch)
            .apply {
                if (sha != null) put("sha", sha)
            }
            .toString()

        request(
            method = "PUT",
            url = contentsUrl(withRef = false),
            body = payload
        )
    }

    private fun currentShaOrNull(): String? {
        return runCatching {
            val response = request(
                method = "GET",
                url = contentsUrl(withRef = true),
                body = null
            )
            JSONObject(response).optString("sha").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun contentsUrl(withRef: Boolean): String {
        val base = "https://api.github.com/repos/${encode(config.owner)}/${encode(config.repo)}/contents/${encodePath(config.path)}"
        return if (withRef) "$base?ref=${encode(config.branch)}" else base
    }

    private fun request(method: String, url: String, body: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Authorization", "Bearer ${config.token}")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        if (body != null) {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(StandardCharsets.UTF_8))
            }
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.use { input ->
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader -> reader.readText() }
        }.orEmpty()

        if (status !in 200..299) {
            throw IllegalStateException("GitHub API error $status: $response")
        }

        return response
    }

    private fun encodePath(path: String): String = path
        .split("/")
        .filter { it.isNotBlank() }
        .joinToString("/") { encode(it) }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

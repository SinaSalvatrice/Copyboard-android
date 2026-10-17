package de.circuitcurios.copyboard

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant

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
        val remote = getRemote() ?: error("GitHub sync file does not exist")
        return normalizePayload(remote.content)
            .put(SnippetStore.SYNC_MERGE_MARKER, true)
            .toString()
    }

    fun save(content: String) {
        val remote = getRemote()
        val localPayload = normalizePayload(content)
        val mergedPayload = if (remote == null) {
            localPayload
        } else {
            mergePayloads(normalizePayload(remote.content), localPayload)
        }

        val encoded = Base64.encodeToString(
            mergedPayload.toString(2).toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
        val payload = JSONObject()
            .put("message", "Sync Copyboard data")
            .put("content", encoded)
            .put("branch", config.branch)
            .apply {
                if (remote?.sha != null) put("sha", remote.sha)
            }
            .toString()

        request(
            method = "PUT",
            url = contentsUrl(withRef = false),
            body = payload
        )
    }

    private fun getRemote(): RemoteFile? {
        return runCatching {
            val response = request(
                method = "GET",
                url = contentsUrl(withRef = true),
                body = null
            )
            val json = JSONObject(response)
            val sha = json.optString("sha").takeIf { it.isNotBlank() }
            val encoded = json.getString("content").replace("\n", "")
            val decoded = String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8)
            RemoteFile(sha = sha, content = decoded)
        }.getOrNull()
    }

    private fun normalizePayload(raw: String): JSONObject {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("[")) {
            JSONObject()
                .put("version", 1)
                .put("updatedAt", Instant.EPOCH.toString())
                .put("snippets", JSONArray(trimmed))
                .put("groups", JSONArray())
                .put("folders", JSONArray())
                .put("groupFolders", JSONObject())
        } else {
            JSONObject(trimmed).apply {
                if (!has("version")) put("version", 1)
                if (!has("updatedAt")) put("updatedAt", Instant.EPOCH.toString())
                if (!has("groups")) put("groups", JSONArray())
                if (!has("folders")) put("folders", JSONArray())
                if (!has("groupFolders")) put("groupFolders", JSONObject())
            }
        }
    }

    private fun mergePayloads(remote: JSONObject, local: JSONObject): JSONObject {
        val snippets = linkedMapOf<String, JSONObject>()
        addSnippets(snippets, remote.optJSONArray("snippets"))
        addSnippets(snippets, local.optJSONArray("snippets"))

        val groups = mergeStringArrays(remote.optJSONArray("groups"), local.optJSONArray("groups"))
        val folders = mergeStringArrays(remote.optJSONArray("folders"), local.optJSONArray("folders"))
        val assignments = JSONObject()
        copyObject(remote.optJSONObject("groupFolders"), assignments)
        copyObject(local.optJSONObject("groupFolders"), assignments)

        return JSONObject()
            .put("version", 2)
            .put("app", "Copyboard")
            .put("updatedAt", Instant.now().toString())
            .put("snippets", JSONArray(snippets.values.toList()))
            .put("groups", groups)
            .put("folders", folders)
            .put("groupFolders", assignments)
    }

    private fun addSnippets(target: MutableMap<String, JSONObject>, source: JSONArray?) {
        if (source == null) return
        for (index in 0 until source.length()) {
            val candidate = source.optJSONObject(index) ?: continue
            val id = candidate.optString("id").takeIf { it.isNotBlank() } ?: continue
            val current = target[id]
            if (current == null || timestamp(candidate.optString("updatedAt")) >= timestamp(current.optString("updatedAt"))) {
                target[id] = JSONObject(candidate.toString())
            }
        }
    }

    private fun mergeStringArrays(first: JSONArray?, second: JSONArray?): JSONArray {
        val values = linkedSetOf<String>()
        listOf(first, second).forEach { array ->
            if (array == null) return@forEach
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let(values::add)
            }
        }
        return JSONArray(values.sorted())
    }

    private fun copyObject(source: JSONObject?, target: JSONObject) {
        if (source == null) return
        source.keys().forEach { key ->
            if (source.isNull(key)) target.put(key, JSONObject.NULL)
            else target.put(key, source.opt(key))
        }
    }

    private fun timestamp(value: String): Long = runCatching {
        Instant.parse(value).toEpochMilli()
    }.getOrDefault(0L)

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

    private data class RemoteFile(
        val sha: String?,
        val content: String
    )
}

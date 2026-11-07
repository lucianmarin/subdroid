package com.lucianmarin.sub.network

import com.lucianmarin.sub.model.ActionStatusResponse
import com.lucianmarin.sub.model.AuthResponse
import com.lucianmarin.sub.model.MemberResponse
import com.lucianmarin.sub.model.PaginatedChatsResponse
import com.lucianmarin.sub.model.PaginatedEntriesResponse
import com.lucianmarin.sub.model.PaginatedUsersResponse
import com.lucianmarin.sub.model.ReplyDetailResponse
import com.lucianmarin.sub.model.SubChat
import com.lucianmarin.sub.model.SubEntry
import com.lucianmarin.sub.model.SubNotifications
import com.lucianmarin.sub.model.SubUser
import com.lucianmarin.sub.model.UserUpdateResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.reflect.Field
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class APIException(
    override val message: String,
    val statusCode: Int = 0,
    val fieldErrors: Map<String, String> = emptyMap()
) : Exception(message)

class APIClient(
    @Volatile var baseUrl: String = DEFAULT_BASE_URL,
    @Volatile var authToken: String? = null
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://subreply.com/api"

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            encodeDefaults = true
        }

        fun normalizeBaseUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return DEFAULT_BASE_URL
            return if (trimmed.endsWith("/api")) {
                trimmed
            } else if (trimmed == "https://subreply.com" || trimmed == "http://subreply.com") {
                "$trimmed/api"
            } else {
                trimmed
            }
        }
    }

    private fun buildUrl(path: String, queryParams: Map<String, String?> = emptyMap()): URL {
        val cleanBase = normalizeBaseUrl(baseUrl)
        val cleanPath = path.trimStart('/')
        val filtered = queryParams.entries.filter { !it.value.isNullOrBlank() }
        val queryString = if (filtered.isEmpty()) {
            ""
        } else {
            "?" + filtered.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v ?: "", "UTF-8")}"
            }
        }
        return URL("$cleanBase/$cleanPath$queryString")
    }

    private fun encodeSegment(segment: String): String =
        URLEncoder.encode(segment.trim(), "UTF-8")

    private fun extractErrors(rawBody: String): Map<String, String> {
        if (rawBody.isBlank()) return emptyMap()
        return try {
            val root = json.parseToJsonElement(rawBody).jsonObject
            val result = mutableMapOf<String, String>()
            val errorsObj = root["errors"] as? JsonObject
            if (errorsObj != null) {
                for ((key, value) in errorsObj) {
                    val text = (value as? JsonPrimitive)?.content ?: value.toString()
                    if (text.isNotBlank()) {
                        result[key] = text
                    }
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun setRequestMethodCompat(conn: HttpURLConnection, method: String) {
        if (method != "PATCH") {
            conn.requestMethod = method
            return
        }
        try {
            conn.requestMethod = "PATCH"
        } catch (_: Exception) {
            try {
                var target: Any = conn
                val delegateField: Field? = try {
                    conn.javaClass.getDeclaredField("delegate").apply { isAccessible = true }
                } catch (_: Exception) {
                    null
                }
                if (delegateField != null) {
                    target = delegateField.get(conn) ?: conn
                }
                var cls: Class<*>? = target.javaClass
                while (cls != null) {
                    try {
                        val methodField = cls.getDeclaredField("method")
                        methodField.isAccessible = true
                        methodField.set(target, "PATCH")
                        break
                    } catch (_: NoSuchFieldException) {
                        cls = cls.superclass
                    }
                }
                if (conn.requestMethod != "PATCH") {
                    val topField = HttpURLConnection::class.java.getDeclaredField("method")
                    topField.isAccessible = true
                    topField.set(conn, "PATCH")
                }
            } catch (_: Exception) {
                conn.requestMethod = "POST"
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            }
        }
    }

    private suspend inline fun <reified T> request(
        method: String,
        path: String,
        queryParams: Map<String, String?> = emptyMap(),
        body: JsonObject? = null
    ): T = withContext(Dispatchers.IO) {
        val rawText = requestRaw(method, path, queryParams, body)
        val fieldErrors = extractErrors(rawText)
        if (fieldErrors.isNotEmpty() &&
            T::class != AuthResponse::class &&
            T::class != UserUpdateResponse::class &&
            T::class != ActionStatusResponse::class
        ) {
            val firstMessage = fieldErrors.values.firstOrNull() ?: "Validation error"
            throw APIException(firstMessage, statusCode = 200, fieldErrors = fieldErrors)
        }
        json.decodeFromString<T>(rawText)
    }

    private suspend fun requestRaw(
        method: String,
        path: String,
        queryParams: Map<String, String?> = emptyMap(),
        body: JsonObject? = null
    ): String = withContext(Dispatchers.IO) {
        val url = buildUrl(path, queryParams)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            setRequestMethodCompat(this, method)
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Subdroid/1.0 (Android)")
            val token = authToken
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: ""

            if (code !in 200..299) {
                val fieldErrors = extractErrors(responseText)
                val errorMsg = fieldErrors.values.firstOrNull()
                    ?: when (code) {
                        401 -> "Unauthorized (401). Please log in."
                        404 -> "Not found (404): $path"
                        else -> "Server error ($code)"
                    }
                throw APIException(errorMsg, code, fieldErrors)
            }

            responseText
        } finally {
            conn.disconnect()
        }
    }

    private fun mapToJsonObject(payload: Map<String, String>): JsonObject =
        buildJsonObject {
            for ((k, v) in payload) {
                put(k, v)
            }
        }

    private fun decodeUserUpdate(rawText: String): UserUpdateResponse {
        val errors = extractErrors(rawText)
        if (errors.isNotEmpty()) {
            return UserUpdateResponse(user = null, errors = errors)
        }
        return try {
            val parsed = json.decodeFromString<UserUpdateResponse>(rawText)
            if (parsed.user != null || !parsed.errors.isNullOrEmpty()) {
                parsed
            } else {
                val directUser = json.decodeFromString<SubUser>(rawText)
                if (directUser.username.isNotBlank()) {
                    UserUpdateResponse(user = directUser, errors = null)
                } else {
                    parsed
                }
            }
        } catch (_: Exception) {
            val directUser = json.decodeFromString<SubUser>(rawText)
            UserUpdateResponse(user = directUser, errors = null)
        }
    }

    // MARK: - Auth

    suspend fun login(username: String, password: String): AuthResponse =
        request(
            method = "POST",
            path = "/login",
            body = buildJsonObject {
                put("username", username)
                put("password", password)
            }
        )

    suspend fun register(payload: Map<String, String>): AuthResponse =
        request(
            method = "POST",
            path = "/register",
            body = mapToJsonObject(payload)
        )

    // MARK: - Notifications

    suspend fun fetchNotifications(): SubNotifications =
        request(
            method = "GET",
            path = "/notifications"
        )

    // MARK: - Feeds & Streams

    suspend fun fetchEntries(
        path: String,
        page: Int = 1,
        query: String? = null
    ): PaginatedEntriesResponse {
        val params = mutableMapOf<String, String?>()
        if (page > 1) {
            params["p"] = page.toString()
        }
        if (!query.isNullOrEmpty()) {
            params["q"] = query
        }
        return request(
            method = "GET",
            path = path,
            queryParams = params
        )
    }

    suspend fun fetchUsers(
        path: String,
        page: Int = 1,
        query: String? = null
    ): PaginatedUsersResponse {
        val params = mutableMapOf<String, String?>()
        if (page > 1) {
            params["p"] = page.toString()
        }
        if (!query.isNullOrEmpty()) {
            params["q"] = query
        }
        return request(
            method = "GET",
            path = path,
            queryParams = params
        )
    }

    // MARK: - Reply Detail & Post Actions

    suspend fun fetchReplyDetail(id: Int): ReplyDetailResponse =
        request(
            method = "GET",
            path = "/reply/$id"
        )

    suspend fun postThread(content: String): SubEntry =
        request(
            method = "POST",
            path = "/thread",
            body = buildJsonObject {
                put("content", content)
            }
        )

    suspend fun postReply(id: Int, content: String): SubEntry =
        request(
            method = "POST",
            path = "/reply/$id",
            body = buildJsonObject {
                put("content", content)
            }
        )

    suspend fun editEntry(id: Int, content: String): SubEntry =
        request(
            method = "PATCH",
            path = "/edit/$id",
            body = buildJsonObject {
                put("content", content)
            }
        )

    suspend fun deleteEntry(id: Int): ActionStatusResponse =
        request(
            method = "POST",
            path = "/delete/$id"
        )

    suspend fun saveEntry(id: Int): ActionStatusResponse =
        request(
            method = "POST",
            path = "/save/$id"
        )

    suspend fun unsaveEntry(id: Int): ActionStatusResponse =
        request(
            method = "POST",
            path = "/unsave/$id"
        )

    // MARK: - Member Profile & Follow

    suspend fun fetchMemberDetail(username: String): SubUser {
        val clean = encodeSegment(username.lowercase())
        val resp = request<MemberResponse>(method = "GET", path = "/$clean")
        return resp.user ?: SubUser(username = username)
    }

    suspend fun fetchMemberPosts(
        username: String,
        page: Int = 1
    ): PaginatedEntriesResponse {
        val clean = encodeSegment(username.lowercase())
        val params = if (page > 1) mapOf("p" to page.toString()) else emptyMap()
        return request(
            method = "GET",
            path = "/$clean/posts",
            queryParams = params
        )
    }

    suspend fun followUser(username: String): ActionStatusResponse =
        request(
            method = "POST",
            path = "/follow/${encodeSegment(username.lowercase())}"
        )

    suspend fun unfollowUser(username: String): ActionStatusResponse =
        request(
            method = "POST",
            path = "/unfollow/${encodeSegment(username.lowercase())}"
        )

    // MARK: - Direct Messages

    suspend fun fetchMessagesInbox(page: Int = 1): PaginatedChatsResponse {
        val params = if (page > 1) mapOf("p" to page.toString()) else emptyMap()
        return request(
            method = "GET",
            path = "/messages",
            queryParams = params
        )
    }

    suspend fun fetchConversation(username: String, page: Int = 1): PaginatedChatsResponse {
        val params = if (page > 1) mapOf("p" to page.toString()) else emptyMap()
        return request(
            method = "GET",
            path = "/${encodeSegment(username.lowercase())}/message",
            queryParams = params
        )
    }

    suspend fun sendMessage(username: String, content: String): SubChat =
        request(
            method = "POST",
            path = "/${encodeSegment(username.lowercase())}/send",
            body = buildJsonObject {
                put("content", content)
            }
        )

    suspend fun unsendMessage(id: Int): ActionStatusResponse =
        request(
            method = "POST",
            path = "/unsend/$id"
        )

    suspend fun clearConversation(username: String): ActionStatusResponse =
        request(
            method = "POST",
            path = "/${encodeSegment(username.lowercase())}/clear"
        )

    // MARK: - Options (Profile & Details)

    suspend fun updateProfile(payload: Map<String, String>): UserUpdateResponse {
        val raw = requestRaw(
            method = "PATCH",
            path = "/profile",
            body = mapToJsonObject(payload)
        )
        return decodeUserUpdate(raw)
    }

    suspend fun updateDetails(payload: Map<String, String>): UserUpdateResponse {
        val raw = requestRaw(
            method = "PATCH",
            path = "/details",
            body = mapToJsonObject(payload)
        )
        return decodeUserUpdate(raw)
    }
}


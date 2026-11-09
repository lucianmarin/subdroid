package com.lucianmarin.sub.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

data class SocialLinkItem(
    val label: String,
    val url: String
)

@Serializable
data class SubUser(
    val followed: Boolean = false,
    val follows: Boolean = false,
    val username: String = "",
    val email: String = "",
    @SerialName("full_name") private val rawFullName: String? = null,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    @SerialName("created_at") val createdAt: Double = 0.0,
    @SerialName("seen_at") val seenAt: Double = 0.0,
    val donated: Double = 0.0,
    val timestamp: String = "",
    val emoji: String = "",
    val birthday: String = "",
    val location: String = "",
    val link: String = "",
    val description: String = "",
    val phone: Map<String, String> = emptyMap(),
    val social: Map<String, String> = emptyMap()
) {
    val fullName: String
        get() = rawFullName?.takeIf { it.isNotBlank() } ?: username

    val cityLabel: String
        get() = location.split(",").firstOrNull()?.trim().orEmpty()

    val socialLinks: List<SocialLinkItem>
        get() {
            val mapping: List<Triple<String, String, (String) -> String>> = listOf(
                Triple("behance", "Behance") { "https://www.behance.net/$it" },
                Triple("dribbble", "Dribbble") { "https://dribbble.com/$it" },
                Triple("figma", "Figma") { "https://www.figma.com/@$it" },
                Triple("github", "GitHub") { "https://github.com/$it" },
                Triple("instagram", "Instagram") { "https://www.instagram.com/$it" },
                Triple("linkedin", "LinkedIn") { "https://www.linkedin.com/in/$it" },
                Triple("medium", "Medium") { "https://medium.com/@$it" },
                Triple("paypal", "PayPal") { "https://www.paypal.com/paypalme/$it" },
                Triple("reddit", "Reddit") { "https://www.reddit.com/user/$it" },
                Triple("spotify", "Spotify") { "https://open.spotify.com/user/$it" },
                Triple("steam", "Steam") { "https://steamcommunity.com/id/$it" },
                Triple("substack", "Substack") { "https://$it.substack.com/" },
                Triple("telegram", "Telegram") { "https://t.me/$it" },
                Triple("tiktok", "TikTok") { "https://www.tiktok.com/@$it" },
                Triple("twitch", "Twitch") { "https://www.twitch.tv/$it" },
                Triple("tumblr", "Tumblr") { "https://$it.tumblr.com/" },
                Triple("vk", "VK") { "https://vk.com/$it" },
                Triple("x", "X") { "https://x.com/$it" },
                Triple("youtube", "YouTube") { "https://www.youtube.com/@$it" }
            )
            return mapping.mapNotNull { (key, label, builder) ->
                val handle = social[key]?.trim().orEmpty()
                if (handle.isNotEmpty()) {
                    SocialLinkItem(label = label, url = builder(handle))
                } else {
                    null
                }
            }
        }

    val phoneLink: SocialLinkItem?
        get() {
            val code = phone["code"]?.trim().orEmpty()
            val number = phone["number"]?.trim().orEmpty()
            if (code.isEmpty() || number.isEmpty()) return null
            val cleanCode = code.removePrefix("+")
            val cleanNum = number.replace(" ", "").replace("-", "")
            return SocialLinkItem(label = "Phone", url = "tel:+$cleanCode$cleanNum")
        }

    val allLinks: List<SocialLinkItem>
        get() {
            val links = socialLinks.toMutableList()
            phoneLink?.let { links.add(it) }
            return links.sortedBy { it.label }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SubUser) return false
        return username.equals(other.username, ignoreCase = true) &&
            followed == other.followed &&
            follows == other.follows &&
            fullName == other.fullName &&
            description == other.description &&
            location == other.location &&
            birthday == other.birthday &&
            link == other.link &&
            emoji == other.emoji &&
            donated == other.donated &&
            phone == other.phone &&
            social == other.social
    }

    override fun hashCode(): Int = username.lowercase().hashCode()
}

@Serializable(with = SubEntrySerializer::class)
data class SubEntry(
    val id: Int = 0,
    val content: String = "",
    @SerialName("created_by") val createdBy: SubUser = SubUser(),
    val saved: Boolean = false,
    val edited: Boolean = false,
    val replies: Int? = null,
    val timestamp: String = "",
    val parent: SubEntry? = null,
    @SerialName("has_explicit_null_parent") val hasExplicitNullParent: Boolean = false,
    val kids: List<SubEntry>? = null
) {
    /**
     * Matches `entry.kids[:5]|reverse` in `templates/common/stream.html` and `Macreply`.
     */
    val streamKids: List<SubEntry>
        get() = kids?.take(5)?.reversed() ?: emptyList()

    /**
     * In `templates/common/entry.html`, `'parent' in entry and not entry.parent` appends `' ¬'`.
     */
    val isRootThread: Boolean
        get() = parent == null && hasExplicitNullParent

    val replyCount: Int
        get() = replies ?: (kids?.size ?: 0)

    fun replyButtonLabel(isRootOverride: Boolean? = null): String {
        val count = replyCount
        val base = when (count) {
            0 -> "reply"
            1 -> "1 reply"
            else -> "$count replies"
        }
        val showCorner = isRootOverride ?: isRootThread
        return if (showCorner) "$base ¬" else base
    }
}

@Serializable
private data class RawSubEntry(
    val id: Int = 0,
    val content: String = "",
    @SerialName("created_by") val createdBy: SubUser = SubUser(),
    val saved: Boolean = false,
    val edited: Boolean = false,
    val replies: Int? = null,
    val timestamp: String = "",
    val parent: SubEntry? = null,
    @SerialName("has_explicit_null_parent") val hasExplicitNullParent: Boolean = false,
    val kids: List<SubEntry>? = null
)

object SubEntrySerializer : kotlinx.serialization.KSerializer<SubEntry> {
    private val delegate = object : JsonTransformingSerializer<RawSubEntry>(RawSubEntry.serializer()) {
        override fun transformDeserialize(element: JsonElement): JsonElement {
            if (element is JsonObject) {
                val hasExplicitNull = element.containsKey("parent") && element["parent"] is JsonNull
                if (hasExplicitNull && !element.containsKey("has_explicit_null_parent")) {
                    val map = element.toMutableMap()
                    map["has_explicit_null_parent"] = JsonPrimitive(true)
                    return JsonObject(map)
                }
            }
            return element
        }
    }

    override val descriptor = delegate.descriptor

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): SubEntry {
        val raw = delegate.deserialize(decoder)
        return SubEntry(
            id = raw.id,
            content = raw.content,
            createdBy = raw.createdBy,
            saved = raw.saved,
            edited = raw.edited,
            replies = raw.replies,
            timestamp = raw.timestamp,
            parent = raw.parent,
            hasExplicitNullParent = raw.hasExplicitNullParent,
            kids = raw.kids
        )
    }

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: SubEntry) {
        val raw = RawSubEntry(
            id = value.id,
            content = value.content,
            createdBy = value.createdBy,
            saved = value.saved,
            edited = value.edited,
            replies = value.replies,
            timestamp = value.timestamp,
            parent = value.parent,
            hasExplicitNullParent = value.hasExplicitNullParent,
            kids = value.kids
        )
        delegate.serialize(encoder, raw)
    }
}

@Serializable
data class SubChat(
    val id: Int = 0,
    val content: String = "",
    @SerialName("created_by") val createdBy: SubUser = SubUser(),
    @SerialName("to_user") val toUser: SubUser = SubUser(),
    val seen: Boolean = false,
    val timestamp: String = ""
)

data class NotificationAlert(
    val section: SidebarSection,
    val message: String
)

@Serializable
data class SubNotifications(
    val followers: Int = 0,
    val mentions: Int = 0,
    val replies: Int = 0,
    val messages: Int = 0
) {
    val activityCount: Int
        get() = followers + mentions + replies

    val total: Int
        get() = followers + mentions + replies + messages

    /**
     * Matches `SubNotifications.newNotifications(since:)` in `../macreply/Sources/Macreply/Models/Models.swift`.
     */
    fun newNotifications(since: SubNotifications): List<NotificationAlert> {
        val alerts = mutableListOf<NotificationAlert>()
        if (replies > since.replies) {
            alerts.add(NotificationAlert(SidebarSection.REPLIES, "Reply received"))
        }
        if (messages > since.messages) {
            alerts.add(NotificationAlert(SidebarSection.MESSAGES, "Message received"))
        }
        if (followers > since.followers) {
            alerts.add(NotificationAlert(SidebarSection.FOLLOWERS, "Follower received"))
        }
        if (mentions > since.mentions) {
            alerts.add(NotificationAlert(SidebarSection.MENTIONS, "Mention received"))
        }
        return alerts
    }

    companion object {
        fun capBadge(count: Int): String? {
            if (count <= 0) return null
            if (count > 99) return "99+"
            return count.toString()
        }
    }
}

// MARK: - API Response Envelopes

@Serializable
data class PaginatedEntriesResponse(
    val page: Int = 1,
    val entries: List<SubEntry> = emptyList()
)

@Serializable
data class PaginatedUsersResponse(
    val page: Int = 1,
    val entries: List<SubUser> = emptyList()
)

@Serializable
data class PaginatedChatsResponse(
    val page: Int = 1,
    val blocked: Boolean? = null,
    val entries: List<SubChat> = emptyList()
)

@Serializable
data class ReplyDetailResponse(
    val entry: SubEntry = SubEntry(),
    val ancestors: List<SubEntry> = emptyList(),
    val kids: List<SubEntry> = emptyList(),
    val duplicate: Boolean = false
)

@Serializable
data class MemberResponse(
    val member: SubUser? = null,
    @SerialName("user") val rawUser: SubUser? = null
) {
    val user: SubUser?
        get() = member ?: rawUser
}

@Serializable
data class AuthResponse(
    val token: String? = null,
    val user: SubUser? = null,
    val errors: Map<String, String>? = null
)

@Serializable
data class UserUpdateResponse(
    val user: SubUser? = null,
    val errors: Map<String, String>? = null
)

@Serializable
data class ActionStatusResponse(
    val status: String? = null,
    val errors: Map<String, String>? = null
)

// MARK: - Navigation & Sidebar Sections (1:1 with `../subreply/templates/sidebar.html` & `../macreply`)

enum class SidebarSection(
    val key: String,
    val title: String,
    val iconName: String,
    val requiresAuth: Boolean = false
) {
    FEED("feed", "Feed", "bolt", requiresAuth = true),
    FOLLOWING("following", "Following", "move_up", requiresAuth = true),
    FOLLOWERS("followers", "Followers", "move_down", requiresAuth = true),
    MENTIONS("mentions", "Mentions", "alternate_email", requiresAuth = true),
    MESSAGES("messages", "Messages", "chat_bubble", requiresAuth = true),
    REPLIES("replies", "Replies", "reply", requiresAuth = true),
    SAVED("saved", "Saved", "bookmark", requiresAuth = true),
    PROFILE("profile", "Profile", "account_circle", requiresAuth = true),
    OPTIONS("options", "Options", "tune", requiresAuth = true),
    ABOUT("about", "About", "info", requiresAuth = false),
    LOGIN("login", "Login", "lock", requiresAuth = false),
    REGISTER("register", "Register", "person_add", requiresAuth = false),
    PEOPLE("people", "People", "group", requiresAuth = false),
    TRENDING("trending", "Trending", "trending_up", requiresAuth = false),
    DISCOVER("discover", "Discover", "language", requiresAuth = false);

    val parentBottomSection: SidebarSection
        get() = when (this) {
            MESSAGES, REPLIES, MENTIONS -> MESSAGES
            TRENDING, SAVED, FOLLOWERS, FOLLOWING -> TRENDING
            PROFILE, OPTIONS -> PEOPLE
            LOGIN, REGISTER -> LOGIN
            else -> this
        }

    companion object {
        val authenticatedSections: List<SidebarSection> = listOf(
            FEED, MESSAGES, TRENDING, PEOPLE, DISCOVER
        )

        val unauthenticatedSections: List<SidebarSection> = listOf(
            ABOUT, LOGIN,
            TRENDING, PEOPLE, DISCOVER
        )

        val messagesSubtabs: List<SidebarSection> = listOf(
            MESSAGES, MENTIONS, REPLIES
        )

        val trendingSubtabs: List<SidebarSection> = listOf(
            TRENDING, SAVED, FOLLOWERS, FOLLOWING
        )

        fun fromKey(key: String?): SidebarSection? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

sealed interface NavigationDestination {
    data class ReplyDetail(val id: Int) : NavigationDestination
    data class Member(val username: String) : NavigationDestination
    data class Conversation(val username: String) : NavigationDestination
    data class EditEntry(val entry: SubEntry) : NavigationDestination
    data object Options : NavigationDestination
}


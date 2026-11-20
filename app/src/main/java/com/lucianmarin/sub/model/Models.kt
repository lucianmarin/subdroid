package com.lucianmarin.sub.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

data class SocialLinkItem(
    val label: String,
    val url: String
)

@Serializable(with = WorkEntrySerializer::class)
data class WorkEntry(
    val startYear: Int,
    val endYear: Int? = null,
    val company: String
)

object WorkEntrySerializer : KSerializer<WorkEntry> {
    private val arraySerializer = JsonArray.serializer()
    override val descriptor: SerialDescriptor = arraySerializer.descriptor

    override fun deserialize(decoder: Decoder): WorkEntry {
        val array = arraySerializer.deserialize(decoder)
        val startYear = array.getOrNull(0)?.jsonPrimitive?.intOrNull ?: 0
        val endElement = array.getOrNull(1)
        val endYear = if (endElement == null || endElement is JsonNull) {
            null
        } else {
            endElement.jsonPrimitive.intOrNull
        }
        val company = array.getOrNull(2)?.jsonPrimitive?.content.orEmpty()
        return WorkEntry(startYear = startYear, endYear = endYear, company = company)
    }

    override fun serialize(encoder: Encoder, value: WorkEntry) {
        val array = JsonArray(
            listOf(
                JsonPrimitive(value.startYear),
                value.endYear?.let { JsonPrimitive(it) } ?: JsonNull,
                JsonPrimitive(value.company)
            )
        )
        arraySerializer.serialize(encoder, array)
    }
}

private fun joinHumanList(parts: List<String>): String = when (parts.size) {
    0 -> ""
    1 -> parts[0]
    else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
}

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
    val donations: Double = 0.0,
    val timestamp: String = "",
    val emoji: String = "",
    val birthday: String = "",
    val location: String = "",
    val link: String = "",
    val description: String = "",
    val phone: Map<String, String> = emptyMap(),
    val social: Map<String, String> = emptyMap(),
    val work: List<WorkEntry> = emptyList()
) {
    val fullName: String
        get() = rawFullName?.takeIf { it.isNotBlank() } ?: username

    val cityLabel: String
        get() = location.split(",").firstOrNull()?.trim().orEmpty()

    val workSummary: String
        get() {
            if (work.isEmpty()) return ""
            val items = work.sortedWith(
                compareByDescending<WorkEntry> { it.startYear }
                    .thenByDescending { it.endYear ?: 9999 }
            )
            val current = items.filter { it.endYear == null }.map { "${it.company} since ${it.startYear}" }
            val pastItems = items.filter { it.endYear != null }
            val past = pastItems.map { it.company }
            val years = pastItems.sumOf { (it.endYear ?: it.startYear) - it.startYear }
            val sentences = mutableListOf<String>()
            if (current.isNotEmpty()) {
                sentences.add("Works at ${joinHumanList(current)}")
                if (past.isNotEmpty()) {
                    sentences.add("Previously at ${joinHumanList(past)} for $years years")
                }
            } else if (past.isNotEmpty()) {
                val startYear = pastItems.minOf { it.startYear }
                val endYear = pastItems.maxOf { it.endYear!! }
                sentences.add("Worked at ${joinHumanList(past)} between $startYear and $endYear for $years years")
            }
            return sentences.joinToString(". ")
        }

    val socialLinks: List<SocialLinkItem>
        get() {
            val mapping: List<Triple<String, String, (String) -> String>> = listOf(
                Triple("behance", "Bēhance") { "https://behance.net/$it" },
                Triple("dribbble", "Dribbble") { "https://dribbble.com/$it" },
                Triple("figma", "Figma") { "https://figma.com/@$it" },
                Triple("github", "GitHub") { "https://github.com/$it" },
                Triple("instagram", "Instagram") { "https://instagram.com/$it" },
                Triple("linkedin", "LinkedIn") { "https://linkedin.com/in/$it" },
                Triple("patreon", "Patreon") { "https://patreon.com/$it" },
                Triple("paypal", "PayPal") { "https://paypal.me/$it" },
                Triple("pinboard", "Pinboard") { "https://pinboard.in/u:$it/" },
                Triple("pinterest", "Pinterest") { "https://pinterest.com/$it" },
                Triple("reddit", "Reddit") { "https://reddit.com/u/$it" },
                Triple("soundcloud", "SoundCloud") { "https://soundcloud.com/$it" },
                Triple("spotify", "Spotify") { "https://open.spotify.com/user/$it" },
                Triple("telegram", "Telegram") { "https://t.me/$it" },
                Triple("threads", "Threads") { "https://threads.com/@$it" },
                Triple("x", "X") { "https://x.com/$it" },
                Triple("youtube", "YouTube") { "https://youtube.com/@$it" }
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
            donations == other.donations &&
            phone == other.phone &&
            social == other.social &&
            work == other.work
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
            if (count > 9) return "+"
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

        val loginSubtabs: List<SidebarSection> = listOf(
            LOGIN, REGISTER
        )

        val subtabsWithIcons: Set<SidebarSection> = (
            messagesSubtabs + trendingSubtabs + loginSubtabs
        ).toSet()

        val authenticatedSwipeOrder: List<SidebarSection> = listOf(
            FEED,
            MESSAGES, MENTIONS, REPLIES,
            TRENDING, SAVED, FOLLOWERS, FOLLOWING,
            PEOPLE,
            DISCOVER
        )

        val unauthenticatedSwipeOrder: List<SidebarSection> = listOf(
            ABOUT,
            LOGIN, REGISTER,
            TRENDING,
            PEOPLE,
            DISCOVER
        )

        fun fromKey(key: String?): SidebarSection? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

enum class OptionsTab(val label: String) {
    PROFILE("Profile"),
    ACCOUNT("Account")
}

sealed interface NavigationDestination {
    data class ReplyDetail(val id: Int) : NavigationDestination
    data class Member(val username: String) : NavigationDestination
    data class Conversation(val username: String) : NavigationDestination
    data class EditEntry(val entry: SubEntry) : NavigationDestination
    data object Options : NavigationDestination
}

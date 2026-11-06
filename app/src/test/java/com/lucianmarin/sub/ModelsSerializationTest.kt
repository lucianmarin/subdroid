package com.lucianmarin.sub

import com.lucianmarin.sub.model.AuthResponse
import com.lucianmarin.sub.model.FeedResponse
import com.lucianmarin.sub.model.MemberResponse
import com.lucianmarin.sub.model.MessagesResponse
import com.lucianmarin.sub.model.ReplyDetailResponse
import com.lucianmarin.sub.model.SubNotifications
import com.lucianmarin.sub.network.APIClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsSerializationTest {

    private val json = APIClient.json

    @Test
    fun decodesSubreplyLoginResponseWithoutUserId() {
        val payload = """
            {
              "token": "abc123token456",
              "user": {
                "username": "lucian",
                "email": "lucian@subreply.com",
                "full_name": "Lucian Marin",
                "first_name": "Lucian",
                "last_name": "Marin",
                "emoji": "☕",
                "birthday": "1985-06-15",
                "location": "Bucharest, Romania",
                "link": "https://lucianmarin.com/",
                "description": "Designer & developer",
                "followed": false,
                "follows": false,
                "created_at": 1735689600.0,
                "seen_at": 1735689900.0,
                "donated": 10.0,
                "timestamp": "1m",
                "phone": {
                  "code": "40",
                  "number": "722000000"
                },
                "social": {
                  "github": "lucianmarin",
                  "x": "lucianmarin"
                }
              }
            }
        """.trimIndent()

        val auth = json.decodeFromString(AuthResponse.serializer(), payload)
        assertEquals("abc123token456", auth.token)
        val user = auth.user
        assertNotNull(user)
        assertEquals("lucian", user?.username)
        assertEquals("Lucian Marin", user?.fullName)
        assertEquals("☕", user?.status)
        assertEquals("Designer & developer", user?.bio)
        assertEquals("Bucharest, Romania", user?.location)
    }

    @Test
    fun decodesSubreplyFeedEntryPayload() {
        val payload = """
            {
              "page": 1,
              "entries": [
                {
                  "id": 9876,
                  "content": "Testing @lucian #subreply /42",
                  "created_by": {
                    "username": "lucian",
                    "full_name": "Lucian Marin",
                    "emoji": "☕"
                  },
                  "created_at": 1735689600.0,
                  "replies": 2,
                  "saved": true,
                  "edited": false,
                  "timestamp": "2m",
                  "parent": {
                    "id": 42,
                    "content": "Original post",
                    "created_by": {
                      "username": "alice",
                      "full_name": "Alice"
                    },
                    "timestamp": "5m"
                  },
                  "link": {
                    "url": "https://subreply.com",
                    "domain": "subreply.com",
                    "title": "Subreply",
                    "description": "Minimalist social network"
                  }
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString(FeedResponse.serializer(), payload)
        assertEquals(1, response.entries.size)
        assertFalse(response.hasNext)

        val entry = response.entries.first()
        assertEquals(9876, entry.id)
        assertEquals("Testing @lucian #subreply /42", entry.text)
        assertEquals("2m", entry.relativeTime)
        assertEquals(2, entry.replyCount)
        assertTrue(entry.isBookmarked)
        assertEquals("lucian", entry.user.username)
        assertEquals("☕", entry.user.status)
        assertEquals(42, entry.parent?.id)
        assertEquals("Original post", entry.parent?.text)
    }


    @Test
    fun decodesReplyDetailMemberMessagesAndNotifications() {
        val replyPayload = """
            {
              "entry": {
                "id": 100,
                "content": "Root thread content",
                "created_by": { "username": "lucian", "full_name": "Lucian Marin" },
                "timestamp": "10m",
                "replies": 1
              },
              "ancestors": [],
              "kids": [
                {
                  "id": 101,
                  "content": "Child reply",
                  "created_by": { "username": "bob", "full_name": "Bob" },
                  "timestamp": "3m"
                }
              ],
              "duplicate": false
            }
        """.trimIndent()

        val replyResp = json.decodeFromString(ReplyDetailResponse.serializer(), replyPayload)
        assertEquals(100, replyResp.parent?.id)
        assertEquals("Root thread content", replyResp.parent?.text)
        assertEquals(1, replyResp.entries.size)
        assertEquals("Child reply", replyResp.entries.first().text)

        val memberPayload = """
            {
              "member": {
                "username": "lucian",
                "full_name": "Lucian Marin",
                "description": "Building Subreply",
                "followed": true,
                "follows": true
              },
              "entries": []
            }
        """.trimIndent()
        val memberResp = json.decodeFromString(MemberResponse.serializer(), memberPayload)
        assertEquals("lucian", memberResp.user?.username)
        assertEquals("Building Subreply", memberResp.user?.bio)
        assertTrue(memberResp.user?.isFollowing == true)
        assertTrue(memberResp.user?.followsMe == true)

        val messagesPayload = """
            {
              "page": 1,
              "entries": [
                {
                  "id": 501,
                  "content": "Hey there!",
                  "created_by": { "username": "alice", "full_name": "Alice" },
                  "to_user": { "username": "lucian", "full_name": "Lucian" },
                  "seen": false,
                  "timestamp": "1m"
                }
              ]
            }
        """.trimIndent()
        val msgResp = json.decodeFromString(MessagesResponse.serializer(), messagesPayload)
        assertEquals(1, msgResp.messages.size)
        assertEquals("Hey there!", msgResp.messages.first().text)
        val partners = msgResp.partnersFor("lucian")
        assertEquals(1, partners.size)
        assertEquals("alice", partners.first().username)

        val notifPayload = """{"followers": 1, "mentions": 2, "replies": 3, "messages": 4}"""
        val notifs = json.decodeFromString(SubNotifications.serializer(), notifPayload)
        assertEquals(6, notifs.activityCount)
        assertEquals(4, notifs.messages)
    }
}

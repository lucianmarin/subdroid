package com.lucianmarin.sub

import com.lucianmarin.sub.model.AuthResponse
import com.lucianmarin.sub.model.MemberResponse
import com.lucianmarin.sub.model.PaginatedChatsResponse
import com.lucianmarin.sub.model.PaginatedEntriesResponse
import com.lucianmarin.sub.model.ReplyDetailResponse
import com.lucianmarin.sub.model.SubNotifications
import com.lucianmarin.sub.network.APIClient
import org.junit.Assert.assertEquals
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
        assertEquals("☕", user?.emoji)
        assertEquals("Designer & developer", user?.description)
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

        val response = json.decodeFromString(PaginatedEntriesResponse.serializer(), payload)
        assertEquals(1, response.entries.size)

        val entry = response.entries.first()
        assertEquals(9876, entry.id)
        assertEquals("Testing @lucian #subreply /42", entry.content)
        assertEquals("2m", entry.timestamp)
        assertEquals(2, entry.replyCount)
        assertTrue(entry.saved)
        assertEquals("lucian", entry.createdBy.username)
        assertEquals("☕", entry.createdBy.emoji)
        assertEquals(42, entry.parent?.id)
        assertEquals("Original post", entry.parent?.content)
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
        assertEquals(100, replyResp.entry.id)
        assertEquals("Root thread content", replyResp.entry.content)
        assertEquals(1, replyResp.kids.size)
        assertEquals("Child reply", replyResp.kids.first().content)

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
        assertEquals("Building Subreply", memberResp.user?.description)
        assertTrue(memberResp.user?.followed == true)
        assertTrue(memberResp.user?.follows == true)

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
        val msgResp = json.decodeFromString(PaginatedChatsResponse.serializer(), messagesPayload)
        assertEquals(1, msgResp.entries.size)
        assertEquals("Hey there!", msgResp.entries.first().content)
        assertEquals("alice", msgResp.entries.first().createdBy.username)

        val notifPayload = """{"followers": 1, "mentions": 2, "replies": 3, "messages": 4}"""
        val notifs = json.decodeFromString(SubNotifications.serializer(), notifPayload)
        assertEquals(6, notifs.activityCount)
        assertEquals(4, notifs.messages)
    }
}

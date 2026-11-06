package com.lucianmarin.sub

import com.lucianmarin.sub.util.ContentParser
import com.lucianmarin.sub.util.ContentToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ContentParserTest {

    @Test
    fun parsesMentionsHashtagsReplyRefsAndLinks() {
        val tokens = ContentParser.parse(
            "Hello (@lucian), check #android and #1234 at https://www.subreply.com/about."
        )

        assertTrue(tokens.contains(ContentToken.Mention("lucian")))
        assertTrue(tokens.contains(ContentToken.Hashtag("android")))
        assertTrue(tokens.contains(ContentToken.ReplyRef(1234)))
        assertTrue(
            tokens.contains(
                ContentToken.Link(
                    display = "subreply.com/about",
                    url = "https://www.subreply.com/about"
                )
            )
        )
    }

    @Test
    fun unwrapsSurroundingPunctuationAndTruncatesLongLinks() {
        val unwrapped = ContentParser.unwrapPunctuation("[@Lucian's]")
        assertEquals("[", unwrapped.first)
        assertEquals("@Lucian", unwrapped.second)
        assertEquals("'s]", unwrapped.third)

        val formatted = ContentParser.formatLink("https://www.example.com/some/very/long/path/")
        assertEquals("example.com/some/v...", formatted)
    }

    @Test
    fun formatsHostnameAgeAndTimeAgo() {
        assertEquals("lucianmarin.com", ContentParser.hostname("https://www.lucianmarin.com/work"))
        assertEquals(39, ContentParser.age("1985-06-15", LocalDate.of(2025, 1, 1)))
        assertEquals("now", ContentParser.timeAgo(1000.0, 1020.0))
        assertEquals("5m", ContentParser.timeAgo(1000.0, 1300.0))
        assertEquals("2h", ContentParser.timeAgo(1000.0, 1000.0 + 7200.0))
    }
}


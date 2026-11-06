package com.lucianmarin.sub.util

import java.net.URI
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Rich content token matching `../subreply/app/filters.py` and `../macreply/Sources/Macreply/Utilities/ContentParser.swift`.
 */
sealed interface ContentToken {
    data class Text(val text: String) : ContentToken
    data class Mention(val username: String) : ContentToken
    data class ReplyRef(val id: Int) : ContentToken
    data class Hashtag(val tag: String) : ContentToken
    data class Link(val display: String, val url: String) : ContentToken
}

object ContentParser {

    /**
     * Parses a Subreply content string into a sequence of inline tokens (`Text`, `Mention`, `ReplyRef`, `Hashtag`, `Link`).
     * Mirrors `app/filters.py:parser(text)` and `Macreply`'s `ContentParser.parse(_:)`.
     */
    fun parse(text: String): List<ContentToken> {
        if (text.isEmpty()) return emptyList()

        val rawWords = text.split(' ')
        val tokens = mutableListOf<ContentToken>()

        for ((index, rawWord) in rawWords.withIndex()) {
            if (index > 0) {
                tokens.add(ContentToken.Text(" "))
            }
            if (rawWord.isEmpty()) continue

            val (prefix, core, suffix) = unwrapPunctuation(rawWord)
            if (prefix.isNotEmpty()) {
                tokens.add(ContentToken.Text(prefix))
            }

            val parsedCore = classifyWord(core)
            tokens.add(parsedCore)

            if (suffix.isNotEmpty()) {
                tokens.add(ContentToken.Text(suffix))
            }
        }

        return mergeAdjacentTextTokens(tokens)
    }

    /**
     * Unwraps surrounding punctuation (`(...)`, `[...]`, `...`, `!!!`, `???`, `'s`, trailing `. , ! ? : ;`)
     * exactly as `app/filters.py:parser(text)` and `Macreply`'s `unwrapPunctuation`.
     */
    fun unwrapPunctuation(word: String): Triple<String, String, String> {
        var prefix = ""
        var suffix = ""
        var current = word

        if (current.length > 3 && (current.endsWith("...") || current.endsWith("!!!") || current.endsWith("???"))) {
            suffix = current.takeLast(3)
            current = current.dropLast(3)
        } else if (current.length > 2 && (current.endsWith("..") || current.endsWith("!!") || current.endsWith("??"))) {
            suffix = current.takeLast(2)
            current = current.dropLast(2)
        } else if (current.length > 1 && current.last() in setOf('.', ',', '!', '?', ':', ';')) {
            suffix = current.last().toString()
            current = current.dropLast(1)
        }

        if (current.length > 1 && current.last() in setOf(')', ']', '}', '\'', '"')) {
            suffix = current.last().toString() + suffix
            current = current.dropLast(1)
        }

        if (current.length > 1 && current.first() in setOf('(', '[', '{', '\'', '"')) {
            prefix = current.first().toString()
            current = current.drop(1)
        }

        if (current.length > 2 && current.endsWith("'s")) {
            suffix = "'s" + suffix
            current = current.dropLast(2)
        }

        return Triple(prefix, current, suffix)
    }


    private fun classifyWord(word: String): ContentToken {
        // 1. @username mention
        if (word.startsWith("@") && word.length > 1) {
            val username = word.drop(1)
            if (username.all { it.isLetterOrDigit() || it == '_' }) {
                return ContentToken.Mention(username.lowercase())
            }
        }

        // 2. #id or #hashtag
        if (word.startsWith("#") && word.length > 1) {
            val body = word.drop(1)
            if (body.all { it.isDigit() }) {
                val id = body.toIntOrNull()
                if (id != null && id > 0) {
                    return ContentToken.ReplyRef(id)
                }
            } else if (body.all { it.isLetterOrDigit() }) {
                return ContentToken.Hashtag(body.lowercase())
            }
        }

        // 3. http:// or https:// link
        if ((word.startsWith("http://") || word.startsWith("https://")) &&
            word.contains(".") &&
            !word.endsWith(".")
        ) {
            val display = formatLink(word)
            return ContentToken.Link(display = display, url = word)
        }

        return ContentToken.Text(word)
    }

    /**
     * Formats a URL display string matching `app/filters.py:link(addr)`:
     * strips `http(s)://`, `www.`, and trailing `/`, and truncates strings > 21 chars to `take(18) + "..."`.
     */
    fun formatLink(rawURL: String): String {
        var display = rawURL
        if (display.startsWith("https://")) {
            display = display.removePrefix("https://")
        } else if (display.startsWith("http://")) {
            display = display.removePrefix("http://")
        }
        if (display.startsWith("www.")) {
            display = display.removePrefix("www.")
        }
        if (display.endsWith("/")) {
            display = display.dropLast(1)
        }
        if (display.length > 21) {
            display = display.take(18) + "..."
        }
        return display
    }

    /**
     * Extracts clean hostname from a profile URL (`app/filters.py:hostname(addr)`).
     */
    fun hostname(from: String): String? {
        if (from.isBlank()) return null
        val host = try {
            URI(from).host
        } catch (_: Exception) {
            null
        } ?: return null
        return if (host.startsWith("www.")) host.removePrefix("www.") else host
    }

    /**
     * Calculates age in years from a `Y-M-D` birthday string (`app/filters.py:age(bday)`).
     */
    fun age(from: String, nowDate: LocalDate = LocalDate.now(ZoneOffset.UTC)): Int? {
        val parts = from.trim().split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        return try {
            val birthDate = LocalDate.of(y, m, d)
            val days = ChronoUnit.DAYS.between(birthDate, nowDate)
            if (days < 0) null else (days / 365.2425).toInt()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Formats a Unix timestamp into Subreply's compact relative string (`now`, `5m`, `2h`, `3d`, `4mo`, `2y`).
     * Mirrors `app/filters.py:timeago(seconds)`.
     */
    fun timeAgo(from: Double, nowEpochSeconds: Double = System.currentTimeMillis() / 1000.0): String {
        if (from <= 0) return ""
        val seconds = maxOf(0.0, nowEpochSeconds - from)
        val minutes = seconds / 60.0
        val hours = minutes / 60.0
        val days = hours / 24.0
        val months = days / 30.436875
        val years = days / 365.2425

        return when {
            minutes < 1 -> "now"
            hours < 1 -> "${minutes.toInt()}m"
            days < 1 -> "${hours.toInt()}h"
            months < 1 -> "${days.toInt()}d"
            years < 1 -> "${months.toInt()}mo"
            else -> "${years.toInt()}y"
        }
    }

    private fun mergeAdjacentTextTokens(tokens: List<ContentToken>): List<ContentToken> {
        val merged = mutableListOf<ContentToken>()
        for (token in tokens) {
            if (token is ContentToken.Text && merged.lastOrNull() is ContentToken.Text) {
                val prev = merged.removeAt(merged.lastIndex) as ContentToken.Text
                merged.add(ContentToken.Text(prev.text + token.text))
            } else {
                merged.add(token)
            }
        }
        return merged
    }
}


package dev.breadcrumb.app

import java.net.URI
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale

object ProblemLinks {
    fun normalize(raw: String): String {
        val text = raw.trim()
        require(text.isNotEmpty()) { "Paste a problem link first." }
        val candidate = if (text.contains("://")) text else "https://$text"
        val uri = runCatching { URI(candidate) }.getOrNull()
            ?: throw IllegalArgumentException("That link is incomplete. Copy the page URL from your browser.")
        require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) {
            "Use a web link starting with https:// or http://."
        }
        require(!uri.host.isNullOrBlank() && uri.host.contains('.') && uri.userInfo == null) {
            "That link needs a valid website address."
        }
        val host = uri.host.lowercase(Locale.ROOT)
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        if (host == "leetcode.com" || host == "www.leetcode.com") {
            if (segments.size >= 2 && segments[0] == "problems") {
                return "https://leetcode.com/problems/${segments[1]}/"
            }
        }
        return URI(uri.scheme.lowercase(Locale.ROOT), null, host, uri.port,
            uri.path.ifEmpty { "/" }, uri.query, null).toASCIIString()
    }

    fun suggestedTitle(raw: String): String = runCatching {
        val uri = URI(normalize(raw))
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val problemIndex = segments.indexOf("problems")
        val slug = if (problemIndex >= 0) segments.getOrNull(problemIndex + 1) else segments.lastOrNull()
        URLDecoder.decode(slug.orEmpty().replace("+", "%2B"), "UTF-8")
            .replace(Regex("\\.(html?|php)$"), "")
            .split('-', '_')
            .filter(String::isNotBlank)
            .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }
    }.getOrDefault("")

    fun isSupportedProblem(raw: String): Boolean = runCatching {
        val uri = URI(normalize(raw))
        val host = uri.host.removePrefix("www.")
        (host == "leetcode.com" || host == "geeksforgeeks.org" || host == "practice.geeksforgeeks.org") &&
            Regex("^/problems/[^/]+").containsMatchIn(uri.path.orEmpty())
    }.getOrDefault(false)

    fun cleanTitle(title: String): String = title
        .replace(Regex("\\s*[-|–]\\s*(LeetCode|GeeksforGeeks|Practice).*$", RegexOption.IGNORE_CASE), "")
        .trim()

    fun scanQuery(text: String): String {
        val lines = text.lines().map(String::trim).filter { it.length >= 6 }
        val numberedTitle = lines.firstOrNull { Regex("^\\d{1,5}[.)]\\s+\\p{L}").containsMatchIn(it) }
        val useful = lines.filterNot {
            Regex("^(description|editorial|solutions?|submissions?|topics|companies|difficulty|easy|medium|hard)$",
                RegexOption.IGNORE_CASE).matches(it)
        }
        return (numberedTitle ?: useful.take(3).joinToString(" "))
            .replace(Regex("\\s+"), " ").take(240).trim()
    }

    fun matches(title: String, query: String): Boolean {
        fun words(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+" )).filter(String::isNotBlank)
        val titleWords = words(title)
        return words(query).all { token -> titleWords.any { it.contains(token) } }
    }

    fun extractSharedLink(text: String): String? = Regex("https?://[^\\s<>]+")
        .find(text)?.value?.trimEnd('.', ',', ')', ']')
}

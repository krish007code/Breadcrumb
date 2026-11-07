package dev.breadcrumb.app

import java.net.URI
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale

enum class ProblemSite(val label: String) {
    LEETCODE("LeetCode"), CODEFORCES("Codeforces"), GEEKSFORGEEKS("GeeksforGeeks"),
    CODECHEF("CodeChef"), ATCODER("AtCoder"), HACKERRANK("HackerRank"), OTHER("Other sites");

    companion object {
        fun fromUrl(url: String): ProblemSite {
            val candidate = if (url.contains("://")) url else "https://$url"
            val host = runCatching { URI(candidate).host.orEmpty().lowercase(Locale.ROOT).removePrefix("www.") }.getOrDefault("")
            return when (host) {
                "leetcode.com" -> LEETCODE
                "codeforces.com" -> CODEFORCES
                "geeksforgeeks.org", "practice.geeksforgeeks.org" -> GEEKSFORGEEKS
                "codechef.com" -> CODECHEF
                "atcoder.jp" -> ATCODER
                "hackerrank.com" -> HACKERRANK
                else -> OTHER
            }
        }

        fun fromStored(value: String?, url: String): ProblemSite = entries.firstOrNull { it.name == value?.trim() } ?: fromUrl(url)
    }
}

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
        if (host == "codeforces.com" || host == "www.codeforces.com") {
            if (segments.size >= 4 && segments[0] == "contest" && segments[2] == "problem") {
                return "https://codeforces.com/problemset/problem/${segments[1]}/${segments[3]}"
            }
            if (segments.size >= 4 && segments[0] == "problemset" && segments[1] == "problem") {
                return "https://codeforces.com/problemset/problem/${segments[2]}/${segments[3]}"
            }
        }
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "${uri.scheme.lowercase(Locale.ROOT)}://$host$port${uri.rawPath.orEmpty().ifEmpty { "/" }}$query"
    }

    fun clean(raw: String): String {
        val normalized = normalize(raw)
        val uri = URI(normalized)
        val host = uri.host.removePrefix("www.")
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        if ((host == "geeksforgeeks.org" || host == "practice.geeksforgeeks.org") && segments.size >= 2 && segments[0] == "problems") {
            return "https://www.geeksforgeeks.org/problems/${segments[1]}/1"
        }
        if (host == "codechef.com") {
            val index = segments.indexOf("problems")
            if (index >= 0 && index + 1 < segments.size) return "https://www.codechef.com/problems/${segments[index + 1]}"
        }
        if (host == "atcoder.jp" && segments.size >= 4 && segments[0] == "contests" && segments[2] == "tasks") {
            return "https://atcoder.jp/contests/${segments[1]}/tasks/${segments[3]}"
        }
        if (host == "hackerrank.com" && segments.size >= 2 && segments[0] == "challenges") {
            return "https://www.hackerrank.com/challenges/${segments[1]}/problem"
        }
        if (host == "codeforces.com" && segments.size >= 4 && segments[0] == "gym" && segments[2] == "problem") {
            return "https://codeforces.com/gym/${segments[1]}/problem/${segments[3]}"
        }
        val tracking = setOf("fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "mc_cid", "mc_eid", "igshid")
        val parameters = uri.rawQuery.orEmpty().split('&').filter(String::isNotBlank).filterNot {
            val key = URLDecoder.decode(it.substringBefore('='), "UTF-8").lowercase(Locale.ROOT)
            key.startsWith("utm_") || key in tracking
        }
        return normalized.substringBefore('?') + if (parameters.isEmpty()) "" else "?${parameters.joinToString("&")}"
    }

    fun suggestedTitle(raw: String): String = runCatching {
        val uri = URI(normalize(raw))
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        if (ProblemSite.fromUrl(raw) == ProblemSite.CODEFORCES && segments.size >= 4) {
            return@runCatching "Codeforces ${segments[segments.size - 2]} ${segments.last()}"
        }
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
        if (host == "codeforces.com") {
            Regex("^/(problemset/problem/\\d+/[A-Za-z0-9]+|gym/\\d+/problem/[A-Za-z0-9]+)/?$").matches(uri.path.orEmpty())
        } else {
            (host == "leetcode.com" || host == "geeksforgeeks.org" || host == "practice.geeksforgeeks.org") &&
                Regex("^/problems/[^/]+").containsMatchIn(uri.path.orEmpty())
        }
    }.getOrDefault(false)

    fun cleanTitle(title: String): String = title
        .replace(Regex("\\s*[-|–]\\s*(LeetCode|GeeksforGeeks|Codeforces|Practice).*$", RegexOption.IGNORE_CASE), "")
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

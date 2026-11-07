package dev.breadcrumb.app

import java.net.URI
import java.util.Locale

internal object CanonicalPaths {
    private data class PathRule(val hosts: Set<String>, val path: Regex, val replacement: String)

    private val basicPatterns =
        listOf(
            PathRule(
                setOf("leetcode.com"),
                Regex("^/problems/([^/]+)"),
                "https://leetcode.com/problems/$1/",
            ),
            PathRule(
                setOf("codeforces.com"),
                Regex("^/contest/([^/]+)/problem/([^/]+)"),
                "https://codeforces.com/problemset/problem/$1/$2",
            ),
            PathRule(
                setOf("codeforces.com"),
                Regex("^/problemset/problem/([^/]+)/([^/]+)"),
                "https://codeforces.com/problemset/problem/$1/$2",
            ),
        )
    private val extraPatterns =
        listOf(
            PathRule(
                setOf("geeksforgeeks.org", "practice.geeksforgeeks.org"),
                Regex("^/problems/([^/]+)"),
                "https://www.geeksforgeeks.org/problems/$1/1",
            ),
            PathRule(
                setOf("codechef.com"),
                Regex("^/(?:[^/]+/)*?problems/([^/]+)"),
                "https://www.codechef.com/problems/$1",
            ),
            PathRule(
                setOf("atcoder.jp"),
                Regex("^/contests/([^/]+)/tasks/([^/]+)"),
                "https://atcoder.jp/contests/$1/tasks/$2",
            ),
            PathRule(
                setOf("hackerrank.com"),
                Regex("^/challenges/([^/]+)"),
                "https://www.hackerrank.com/challenges/$1/problem",
            ),
            PathRule(
                setOf("codeforces.com"),
                Regex("^/gym/([^/]+)/problem/([^/]+)"),
                "https://codeforces.com/gym/$1/problem/$2",
            ),
        )

    private fun canonicalPath(uri: URI, rules: List<PathRule>): String? {
        val host = uri.host.lowercase(Locale.ROOT).removePrefix("www.")
        val path = uri.path.orEmpty().replace(Regex("/+"), "/")
        return rules
            .filter { host in it.hosts }
            .firstNotNullOfOrNull { rule ->
                rule.path.find(path)?.let { rule.path.replace(it.value, rule.replacement) }
            }
    }

    fun normalize(uri: URI) = canonicalPath(uri, basicPatterns)

    fun clean(uri: URI) = canonicalPath(uri, extraPatterns)
}

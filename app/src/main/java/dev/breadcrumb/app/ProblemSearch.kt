package dev.breadcrumb.app

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private const val ConnectSeconds = 15L
private const val ReadSeconds = 25L
private const val CallSeconds = 35L
private const val MaxMatches = 5
private const val HttpUnauthorized = 401
private const val HttpPaymentRequired = 402
private const val HttpRateLimited = 429

data class ProblemMatch(val title: String, val url: String)

class ProblemSearch {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(ConnectSeconds, TimeUnit.SECONDS)
            .readTimeout(ReadSeconds, TimeUnit.SECONDS)
            .callTimeout(CallSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

    suspend fun find(query: String, apiKey: String): List<ProblemMatch> =
        withContext(Dispatchers.IO) {
            val url =
                "https://api.search.tinyfish.ai"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("query", query)
                    .addQueryParameter(
                        "include_domains",
                        "leetcode.com,codeforces.com,geeksforgeeks.org",
                    )
                    .addQueryParameter("language", "en")
                    .build()
            val request = Request.Builder().url(url).header("X-API-Key", apiKey).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val message =
                        when (response.code) {
                            HttpUnauthorized ->
                                "Your search key wasn't accepted. Update it in Settings, or search Google."
                            HttpPaymentRequired,
                            HttpRateLimited ->
                                "Automatic search has reached its allowance. You can still search Google."
                            else ->
                                "Automatic search is unavailable. Search Google or paste a link."
                        }
                    throw IOException(message)
                }
                val results =
                    JSONObject(
                            response.body?.string()
                                ?: throw IOException("Search returned an empty response.")
                        )
                        .getJSONArray("results")
                (0 until results.length())
                    .mapNotNull { index ->
                        val result = results.optJSONObject(index) ?: return@mapNotNull null
                        val link = result.optString("url")
                        if (!ProblemLinks.isSupportedProblem(link)) return@mapNotNull null
                        val title =
                            ProblemLinks.cleanTitle(result.optString("title")).ifBlank {
                                ProblemLinks.suggestedTitle(link)
                            }
                        ProblemMatch(title, ProblemLinks.normalize(link))
                    }
                    .distinctBy(ProblemMatch::url)
                    .take(MaxMatches)
            }
        }
}

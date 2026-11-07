package dev.breadcrumb.app

import org.json.JSONArray
import org.json.JSONObject

data class EntryMetadata(
    val id: String,
    val title: String,
    val url: String,
    val photos: List<String>,
    val createdAt: Long,
    val site: ProblemSite,
    val revision: Long = 0,
) {
    init {
        require(id.isNotBlank() && title.isNotBlank()) { "Incomplete solution metadata." }
        require(photos.isNotEmpty() && photos.distinct().size == photos.size) {
            "A solution needs distinct photos."
        }
        require(
            photos.all { it.isNotBlank() && it != "." && it != ".." && '/' !in it && '\\' !in it }
        ) {
            "Invalid photo filename."
        }
        require(revision >= 0) { "Invalid revision." }
    }

    fun toJson(): String =
        JSONObject()
            .put("version", 2)
            .put("id", id)
            .put("title", title)
            .put("url", url)
            .put("photos", JSONArray(photos))
            .put("createdAt", createdAt)
            .put("site", site.name)
            .put("revision", revision)
            .toString(2)

    companion object {
        fun parse(text: String, legacySite: String? = null): EntryMetadata {
            val json = JSONObject(text)
            val version = json.getInt("version")
            require(version in 1..2) { "Unsupported notebook format." }
            val photos =
                if (version == 1) listOf(json.getString("photo"))
                else {
                    val array = json.getJSONArray("photos")
                    (0 until array.length()).map(array::getString)
                }
            val url = json.getString("url")
            return EntryMetadata(
                json.getString("id"),
                json.getString("title"),
                url,
                photos,
                json.getLong("createdAt"),
                ProblemSite.fromStored(json.optString("site", legacySite.orEmpty()), url),
                json.optLong("revision", 0),
            )
        }
    }
}

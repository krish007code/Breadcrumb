package dev.breadcrumb.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryMetadataTest {
    private val oldEntry =
        """{"version":1,"id":"original-id","title":"Two Sum",
        "url":"https://leetcode.com/problems/two-sum/","photo":"solution.jpg","createdAt":123456}"""

    @Test
    fun existingSinglePhotoEntryLoadsWithoutMigration() {
        val metadata = EntryMetadata.parse(oldEntry)
        assertEquals(listOf("solution.jpg"), metadata.photos)
        assertEquals(ProblemSite.LEETCODE, metadata.site)
        assertEquals(0L, metadata.revision)
    }

    @Test
    fun legacyManualCategoryIsRetainedWhenEditingAnOldEntry() {
        val metadata = EntryMetadata.parse(oldEntry, "CODECHEF\n")
        assertEquals(ProblemSite.CODECHEF, EntryMetadata.parse(metadata.toJson()).site)
    }

    @Test
    fun multiplePhotosRoundTripInTheirOriginalOrder() {
        val metadata =
            EntryMetadata.parse(oldEntry)
                .copy(photos = listOf("page-2.png", "page-1.jpg", "page-3.webp"), revision = 1)
        assertEquals(metadata, EntryMetadata.parse(metadata.toJson()))
    }

    @Test
    fun editsKeepEntryIdentityAndOriginalActivityDate() {
        val original = EntryMetadata.parse(oldEntry)
        val edited =
            original.copy(title = "My revised solution", photos = listOf("new.jpg"), revision = 2)
        val restored = EntryMetadata.parse(edited.toJson())
        assertEquals(original.id, restored.id)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(listOf("new.jpg"), restored.photos)
    }

    @Test
    fun partialMetadataAndInvalidPhotoListsAreRejected() {
        assertTrue(runCatching { EntryMetadata.parse(oldEntry.dropLast(10)) }.isFailure)
        val metadata = EntryMetadata.parse(oldEntry)
        listOf(emptyList(), listOf("../another.jpg"), listOf("photo.jpg", "photo.jpg"), listOf(""))
            .forEach { photos ->
                assertTrue(runCatching { metadata.copy(photos = photos) }.isFailure)
            }
    }
}

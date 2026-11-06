package com.fmhub24.provider.archive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.json.JSONObject

/**
 * The fixtures use the shapes `archive.org/advancedsearch.php` really returns: fields arrive as
 * strings or as arrays depending on the item, the year is often a string, and descriptions are
 * full of HTML.
 */
class ArchiveProviderTest {
    private val provider = ArchiveProvider()

    @Test
    fun `maps a search document to a title`() {
        val doc = JSONObject(
            """
            {
              "identifier": "plan_9_from_outer_space",
              "title": "Plan 9 from Outer Space",
              "year": "1959",
              "subject": ["Science fiction", "Horror"],
              "description": ["<p>Aliens resurrect the dead.</p>"],
              "publicdate": "2005-03-31 00:00:00",
              "addeddate": "2005-03-31 00:00:00",
              "avg_rating": "4.6"
            }
            """.trimIndent(),
        )

        val media = provider.docToMedia(doc)!!

        assertEquals("plan_9_from_outer_space", media.id)
        assertEquals("Plan 9 from Outer Space", media.title)
        assertEquals("movie", media.contentType)
        assertEquals(1959, media.releaseYear)
        assertEquals("https://archive.org/services/img/plan_9_from_outer_space", media.posterUrl)
        assertEquals("Aliens resurrect the dead.", media.description)
        assertEquals(listOf("Science fiction", "Horror"), media.genres)
        assertEquals(4.6, media.rating)
        assertEquals("2005-03-31 00:00:00", media.updatedAt)
        assertEquals("plan_9_from_outer_space", media.canonicalKey)
    }

    @Test
    fun `joins array fields and reads the year out of free text`() {
        val doc = JSONObject(
            """
            {
              "identifier": "metropolis",
              "title": "Metropolis",
              "year": "released 1927, restored later",
              "description": ["First paragraph.", "Second paragraph."],
              "subject": "Silent;Science fiction"
            }
            """.trimIndent(),
        )

        val media = provider.docToMedia(doc)!!

        assertEquals(1927, media.releaseYear)
        assertEquals("First paragraph.; Second paragraph.", media.description)
        assertEquals(listOf("Silent", "Science fiction"), media.genres)
    }

    @Test
    fun `drops documents without an identifier`() {
        assertNull(provider.docToMedia(JSONObject("""{"title": "No identifier"}""")))
    }

    @Test
    fun `falls back to the identifier when the title is missing`() {
        val media = provider.docToMedia(JSONObject("""{"identifier": "untitled_item"}"""))!!
        assertEquals("untitled_item", media.title)
        assertNull(media.releaseYear)
        assertNull(media.rating)
    }
}

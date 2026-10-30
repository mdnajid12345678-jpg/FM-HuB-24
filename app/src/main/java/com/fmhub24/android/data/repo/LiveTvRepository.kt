package com.fmhub24.android.data.repo

import com.fmhub24.android.data.model.ContentType
import com.fmhub24.android.data.model.DataException
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.data.model.MediaSource
import com.fmhub24.android.data.model.mediaSourceFromJson
import com.fmhub24.android.data.remote.SupabaseRest
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * Live TV has its own Supabase tables and is intentionally independent of the public media catalog.
 * Channel rows are read from live_tv_channels; playback sources come from live_tv_sources.
 */
@Singleton
class LiveTvRepository @Inject constructor(
    private val rest: SupabaseRest,
) {
    suspend fun channels(): List<MediaItem> = rest.select(
        table = "live_tv_channels",
        query = listOf(
            "select" to "*",
            "status" to "eq.live",
            "order" to "sort_order.asc,name.asc",
        ),
    ).mapNotNull(::channelFromJson)

    suspend fun sources(channelId: String): List<MediaSource> = rest.select(
        table = "live_tv_sources",
        query = listOf(
            "select" to "*",
            "channel_id" to "eq.$channelId",
            "status" to "eq.resolved",
            "order" to "quality.desc.nullslast,server_name.asc",
        ),
    ).mapNotNull { json ->
        // Keep the shared source model/player code, while translating this dedicated schema's FK.
        runCatching {
            val playerRow = JSONObject(json.toString())
            playerRow.put("content_id", json.optString("channel_id"))
            mediaSourceFromJson(playerRow)
        }.getOrNull()
    }.filter { it.isPlayable }

    private fun channelFromJson(json: JSONObject): MediaItem? {
        val id = json.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: return null
        val name = json.optString("name").takeIf { it.isNotBlank() }
            ?: json.optString("title").takeIf { it.isNotBlank() }
            ?: return null
        val category = json.optString("category").takeIf { it.isNotBlank() && it != "null" }
        return MediaItem(
            id = id,
            canonicalKey = json.optString("slug").takeIf { it.isNotBlank() && it != "null" },
            title = name,
            originalTitle = null,
            contentType = ContentType.LIVE,
            releaseYear = null,
            posterUrl = json.optString("logo_url").takeIf { it.isNotBlank() && it != "null" },
            description = json.optString("description").takeIf { it.isNotBlank() && it != "null" },
            genres = listOfNotNull(category),
            status = json.optString("status").takeIf { it.isNotBlank() && it != "null" },
            rating = null,
            createdAt = json.optString("created_at").takeIf { it.isNotBlank() && it != "null" },
            updatedAt = json.optString("updated_at").takeIf { it.isNotBlank() && it != "null" },
        )
    }
}

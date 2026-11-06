package com.fmhub24.provider.iptv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fixture copies the exact shapes of `iptv-org.github.io/iptv/index.m3u`: quoted attributes,
 * a feed marker in tvg-id, a semicolon separated group-title, a quality marker in the name, and a
 * per-stream #EXTVLCOPT line before the URL.
 */
class IptvOrgProviderTest {
    private val playlist = """
        #EXTM3U
        #EXTINF:-1 tvg-id="00sReplay.us@SD" tvg-logo="https://images.pluto.tv/logo.png" group-title="Movies",00s Replay
        https://jmp2.uk/plu-62ba60f059624e000781c436.m3u8
        #EXTINF:-1 tvg-id="1Plus1International.ua@SD" tvg-logo="https://i.imgur.com/LOY0rtp.png" http-user-agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64)" group-title="General",1+1 International
        #EXTVLCOPT:http-user-agent=Mozilla/5.0 (Linux; Android 14)
        http://158.101.222.193:88/georgia_play.php?id=1plus1international
        #EXTINF:-1 tvg-id="2GB.au@SD" tvg-logo="https://i.ibb.co/jwM8DFG/2GB-1.png" group-title="News;Public",2GB (1080p)
        https://2gblive.akamaized.net/hls/live/2033805/2GB/index.m3u8
    """.trimIndent()

    @Test
    fun `reads channels, groups, logos and quality`() {
        val parsed = IptvOrgProvider().parsePlaylist(playlist)

        assertEquals(
            listOf("iptv:00sReplay.us", "iptv:1Plus1International.ua", "iptv:2GB.au"),
            parsed.channels.map { it.id },
        )
        assertTrue(parsed.channels.all { it.contentType == "live" })

        val news = parsed.channels.first { it.id == "iptv:2GB.au" }
        // "News;Public" is the first group, and the quality marker is not part of the name.
        assertEquals(listOf("News"), news.genres)
        assertEquals("2GB", news.title)
        assertEquals("https://i.ibb.co/jwM8DFG/2GB-1.png", news.posterUrl)

        val newsStream = parsed.streams.getValue("iptv:2GB.au").single()
        assertEquals("M3U8", newsStream.linkType)
        assertEquals(1080, newsStream.quality)
    }

    @Test
    fun `keeps the request headers a stream needs`() {
        val parsed = IptvOrgProvider().parsePlaylist(playlist)
        val stream = parsed.streams.getValue("iptv:1Plus1International.ua").single()

        assertEquals("VIDEO", stream.linkType)
        assertEquals(1, parsed.streams.getValue("iptv:1Plus1International.ua").size)
        // The #EXTVLCOPT line sits closest to the URL, so it wins over the EXTINF attribute.
        assertEquals("Mozilla/5.0 (Linux; Android 14)", stream.headers["User-Agent"])
    }

    @Test
    fun `drops entries that have no stream url`() {
        val broken = """
            #EXTM3U
            #EXTINF:-1 tvg-id="noUrl.tv",No URL
            #EXTINF:-1 tvg-id="hasUrl.tv",Has URL
            https://example.com/live.m3u8
        """.trimIndent()

        val parsed = IptvOrgProvider().parsePlaylist(broken)

        assertEquals(listOf("iptv:hasUrl.tv"), parsed.channels.map { it.id })
        assertEquals(emptyList(), parsed.streams["iptv:noUrl.tv"].orEmpty())
    }

    @Test
    fun `keeps several streams of the same channel as separate sources`() {
        val multi = """
            #EXTM3U
            #EXTINF:-1 tvg-id="one.tv@SD" group-title="News",One (720p)
            https://example.com/one-a.m3u8
            #EXTINF:-1 tvg-id="one.tv@HD" group-title="News",One (1080p)
            https://example.com/one-b.m3u8
        """.trimIndent()

        val parsed = IptvOrgProvider().parsePlaylist(multi)

        assertEquals(listOf("iptv:one.tv"), parsed.channels.map { it.id })
        assertEquals(2, parsed.streams.getValue("iptv:one.tv").size)
    }
}

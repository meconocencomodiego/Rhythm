/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class OnlineTrackResult(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val webUrl: String,
    val hdArtworkUrl: String? = null
)

object NewPipeAudioExtractor {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Busca pistas en YouTube usando NewPipeExtractor y consulta
     * la carátula HD en Deezer para cada resultado.
     */
    suspend fun searchTracks(query: String): List<OnlineTrackResult> = withContext(Dispatchers.IO) {
        try {
            val searchExtractor: SearchExtractor = ServiceList.YouTube.getSearchExtractor(query)
            searchExtractor.fetchPage()

            val items = searchExtractor.initialPage?.items ?: emptyList()
            
            items.filterIsInstance<StreamInfoItem>().take(15).map { item ->
                val title = item.name ?: ""
                val artist = item.uploaderName ?: ""
                val ytThumbnail = item.thumbnails?.firstOrNull()?.url ?: ""
                
                // Buscar carátula en Deezer
                val deezerCover = fetchDeezerCover(artist, title)

                OnlineTrackResult(
                    id = item.url.orEmpty(),
                    title = cleanTrackTitle(title),
                    artist = artist,
                    durationSeconds = item.duration,
                    thumbnailUrl = ytThumbnail,
                    webUrl = item.url.orEmpty(),
                    hdArtworkUrl = deezerCover ?: ytThumbnail
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Resuelve el enlace de stream directo de audio (.m4a) de mejor calidad.
     */
    suspend fun resolveAudioStream(webUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val streamExtractor: StreamExtractor = ServiceList.YouTube.getStreamExtractor(webUrl)
            streamExtractor.fetchPage()

            val audioStreams = streamExtractor.audioStreams
            if (!audioStreams.isNullOrEmpty()) {
                // Preferir formato M4A / AAC por compatibilidad nativa con ExoPlayer en Android
                val m4aStream = audioStreams
                    .filter { it.format?.name.equals("M4A", ignoreCase = true) }
                    .maxByOrNull { it.averageBitrate }

                m4aStream?.content ?: audioStreams.maxByOrNull { it.averageBitrate }?.content
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Consulta la API pública de Deezer para obtener la portada en tamaño de estudio (1000x1000).
     */
    private fun fetchDeezerCover(artist: String, title: String): String? {
        return try {
            val cleanQuery = cleanTrackTitle(title).replace(Regex("(?i)ft\\.?|feat\\.?", RegexOption.IGNORE_CASE), "")
            val encodedQuery = URLEncoder.encode("$artist $cleanQuery", StandardCharsets.UTF_8.toString())
            val request = Request.Builder()
                .url("https://api.deezer.com/search?q=$encodedQuery&limit=1")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyString = response.body?.string() ?: return null
                val json = JSONObject(bodyString)
                val data = json.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val trackObj = data.getJSONObject(0)
                    val albumObj = trackObj.optJSONObject("album")
                    albumObj?.optString("cover_xl")?.takeIf { it.isNotBlank() }
                        ?: albumObj?.optString("cover_big")?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Elimina etiquetas comunes de YouTube como "(Official Video)", "[Audio]", etc.
     */
    private fun cleanTrackTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\s*[\\[(]?(official|video|audio|lyric|hd|4k|visualizer|remastered)[\\])]?\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}

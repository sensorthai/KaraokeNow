package com.karaoketv.app.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class YouTubeSearchResponse(
    val items: List<YouTubeSearchItem> = emptyList()
)

data class YouTubeSearchItem(
    val id: YouTubeResourceId,
    val snippet: YouTubeSnippet
)

data class YouTubeResourceId(
    val kind: String? = null,
    val videoId: String? = null,
    val playlistId: String? = null
)

data class YouTubeSnippet(
    val title: String = "",
    val channelTitle: String = "",
    val thumbnails: YouTubeThumbnails? = null
)

data class YouTubeThumbnails(
    val medium: YouTubeThumbnail? = null,
    val high: YouTubeThumbnail? = null
)

data class YouTubeThumbnail(
    val url: String = ""
)

interface YouTubeApiService {
    @GET("youtube/v3/search")
    suspend fun searchKaraokeVideos(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("videoCategoryId") videoCategoryId: String = "10",
        @Query("videoEmbeddable") videoEmbeddable: String = "true",
        @Query("maxResults") maxResults: Int = 20,
        @Query("key") apiKey: String
    ): YouTubeSearchResponse

    companion object {
        private const val BASE_URL = "https://www.googleapis.com/"

        fun create(): YouTubeApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(YouTubeApiService::class.java)
        }
    }
}

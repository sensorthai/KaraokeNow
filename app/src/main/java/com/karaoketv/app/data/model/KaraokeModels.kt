package com.karaoketv.app.data.model

enum class SearchFilter(val label: String) {
    ALL("ทั้งหมด"),
    TITLE("ชื่อเพลง"),
    ARTIST("นักร้อง"),
    STYLE("สไตล์เพลง")
}

data class SongItem(
    val videoId: String,
    val title: String,
    val artistName: String,
    val styleTag: String,
    val thumbnailUrl: String,
    val channelTitle: String = "",
    val songCode: String = "",
    val eraTag: String = ""
)

data class ArtistProfile(
    val name: String,
    val styleHint: String,
    val avatarColorHex: Long,
    val songCount: Int = 0
)

data class MusicStyle(
    val id: String,
    val name: String,
    val queryKeyword: String,
    val accentColorHex: Long,
    val songCount: Int = 0
)

data class ChannelProfile(
    val name: String,
    val styleHint: String,
    val accentColorHex: Long,
    val songCount: Int = 0
)

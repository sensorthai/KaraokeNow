package com.karaoketv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.karaoketv.app.data.local.SearchHistoryEntity
import com.karaoketv.app.data.model.SearchFilter
import com.karaoketv.app.data.model.SongItem
import com.karaoketv.app.ui.components.SongCard
import com.karaoketv.app.ui.theme.KaraokeColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    selectedFilter: SearchFilter,
    catalogSongs: List<SongItem> = emptyList(),
    results: List<SongItem>,
    history: List<SearchHistoryEntity>,
    isSearching: Boolean,
    favoriteIds: Set<String> = emptySet(),
    onQueryChange: (String) -> Unit,
    onFilterSelect: (SearchFilter) -> Unit,
    onSubmitSearch: (String, SearchFilter) -> Unit,
    onPlayNow: (SongItem) -> Unit,
    onAddToQueue: (SongItem) -> Unit,
    onToggleFavorite: (SongItem) -> Unit = {},
    onSearchFieldFocusChange: (Boolean) -> Unit = {}
) {
    var showTvKeyboard by remember { mutableStateOf(false) }

    val displayedSongs = remember(catalogSongs, results, query, selectedFilter) {
        val q = query.trim().lowercase()
        val localMatches = if (q.isEmpty()) {
            catalogSongs
        } else {
            catalogSongs.filter { song ->
                when (selectedFilter) {
                    SearchFilter.TITLE -> song.songCode.contains(q) || song.title.lowercase().contains(q)
                    SearchFilter.ARTIST -> song.artistName.lowercase().contains(q)
                    SearchFilter.STYLE -> song.styleTag.lowercase().contains(q)
                    SearchFilter.ALL -> song.songCode.contains(q) ||
                        song.title.lowercase().contains(q) ||
                        song.artistName.lowercase().contains(q) ||
                        song.styleTag.lowercase().contains(q) ||
                        song.channelTitle.lowercase().contains(q)
                }
            }
        }
        if (results.isNotEmpty() && q.isNotEmpty()) {
            (localMatches + results).distinctBy { it.videoId }
        } else {
            localMatches
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ค้นหาเพลงคาราโอเกะ (${displayedSongs.size} / ${catalogSongs.size} เพลง)",
                color = KaraokeColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = { showTvKeyboard = !showTvKeyboard }) {
                Text(if (showTvKeyboard) "ซ่อนแป้นพิมพ์รีโมท" else "แป้นพิมพ์รีโมท", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("ค้นหาชื่อเพลง, นักร้อง, สไตล์, ช่อง หรือรหัส 5 หลัก...") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onSubmitSearch(query, selectedFilter) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = KaraokeColors.TextPrimary,
                    unfocusedTextColor = KaraokeColors.TextPrimary,
                    focusedContainerColor = KaraokeColors.Surface,
                    unfocusedContainerColor = KaraokeColors.Surface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onSearchFieldFocusChange(it.isFocused) }
            )
            Button(onClick = { onSubmitSearch(query, selectedFilter) }) {
                Text(if (isSearching) "กำลังค้นหา..." else "ค้นหา YouTube")
            }
            if (query.isNotEmpty()) {
                OutlinedButton(onClick = { onQueryChange("") }) {
                    Text("ล้าง")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SearchFilter.entries) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelect(filter) }
                ) {
                    Text(filter.label)
                }
            }
        }

        val quickSuggestions = remember {
            listOf("เบิร์ด", "คาราบาว", "บอดี้สแลม", "ลาบานูน", "ปาล์มมี่", "ลูกทุ่ง", "สตริง", "เพื่อชีวิต")
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                Text(
                    text = if (history.isNotEmpty()) "ล่าสุด/แนะนำ:" else "คำค้นยอดนิยม:",
                    color = KaraokeColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
            items(history.take(5), key = { "hist_${it.query}" }) { item ->
                OutlinedButton(
                    onClick = {
                        val filter = runCatching { SearchFilter.valueOf(item.filterType) }
                            .getOrDefault(SearchFilter.ALL)
                        onSubmitSearch(item.query, filter)
                    }
                ) {
                    Text(item.query, fontSize = 12.sp)
                }
            }
            items(quickSuggestions, key = { "sug_$it" }) { word ->
                OutlinedButton(onClick = { onSubmitSearch(word, selectedFilter) }) {
                    Text(word, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showTvKeyboard) {
                TvRemoteKeyboard(
                    onAppend = { ch -> onQueryChange(query + ch) },
                    onBackspace = { if (query.isNotEmpty()) onQueryChange(query.dropLast(1)) },
                    onSpace = { onQueryChange("$query ") },
                    onClear = { onQueryChange("") },
                    onSearch = { onSubmitSearch(query, selectedFilter) },
                    modifier = Modifier
                        .width(380.dp)
                        .fillMaxHeight()
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 250.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(KaraokeColors.Background)
            ) {
                items(displayedSongs, key = { it.videoId }) { song ->
                    SongCard(
                        song = song,
                        onPlayNow = onPlayNow,
                        onAddToQueue = onAddToQueue,
                        isFavorite = song.videoId in favoriteIds,
                        onToggleFavorite = onToggleFavorite
                    )
                }
            }
        }
    }
}

private enum class KeyboardTab(val label: String) {
    THAI("ก-ฮ"),
    VOWELS("สระ/วรรณยุกต์"),
    ENG("A-Z"),
    NUM("0-9")
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvRemoteKeyboard(
    onAppend: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(KeyboardTab.THAI) }

    val keys = remember(activeTab) {
        when (activeTab) {
            KeyboardTab.THAI -> listOf(
                "ก", "ข", "ค", "ฆ", "ง", "จ", "ฉ", "ช",
                "ซ", "ฌ", "ญ", "ฎ", "ฏ", "ฐ", "ฑ", "ฒ",
                "ณ", "ด", "ต", "ถ", "ท", "ธ", "น", "บ",
                "ป", "ผ", "ฝ", "พ", "ฟ", "ภ", "ม", "ย",
                "ร", "ล", "ว", "ศ", "ษ", "ส", "ห", "ฬ",
                "อ", "ฮ"
            )
            KeyboardTab.VOWELS -> listOf(
                "ะ", "า", "ิ", "ี", "ึ", "ื", "ุ", "ู",
                "เ", "แ", "โ", "ใ", "ไ", "ำ", "ั", "็",
                "่", "้", "๊", "๋", "์", "ๆ", "ฤ", "ฦ"
            )
            KeyboardTab.ENG -> ('A'..'Z').map { it.toString() }
            KeyboardTab.NUM -> listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(KaraokeColors.Surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeyboardTab.entries.forEach { tab ->
                FilterChip(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab }
                ) {
                    Text(tab.label, fontSize = 11.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(onClick = onSpace, modifier = Modifier.weight(1.2f)) {
                Text("เว้นวรรค", fontSize = 11.sp)
            }
            OutlinedButton(onClick = onBackspace, modifier = Modifier.weight(1f)) {
                Text("⌫ ลบ", fontSize = 11.sp)
            }
            OutlinedButton(onClick = onClear, modifier = Modifier.weight(0.9f)) {
                Text("ล้าง", fontSize = 11.sp)
            }
            Button(
                onClick = onSearch,
                colors = ButtonDefaults.colors(containerColor = KaraokeColors.Primary),
                modifier = Modifier.weight(1f)
            ) {
                Text("ค้นหา", fontSize = 11.sp, color = Color.White)
            }
        }

        val cols = if (activeTab == KeyboardTab.NUM) 5 else 8
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(keys) { ch ->
                Surface(
                    onClick = { onAppend(ch) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = KaraokeColors.SurfaceElevated,
                        focusedContainerColor = KaraokeColors.Primary,
                        contentColor = KaraokeColors.TextPrimary,
                        focusedContentColor = Color.White
                    ),
                    modifier = Modifier.height(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = ch,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

package com.karaoketv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.OutlinedButton
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
    results: List<SongItem>,
    history: List<SearchHistoryEntity>,
    isSearching: Boolean,
    favoriteIds: Set<String> = emptySet(),
    onQueryChange: (String) -> Unit,
    onFilterSelect: (SearchFilter) -> Unit,
    onSubmitSearch: (String, SearchFilter) -> Unit,
    onPlayNow: (SongItem) -> Unit,
    onAddToQueue: (SongItem) -> Unit,
    onToggleFavorite: (SongItem) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Text(
            text = "ค้นหาเพลงคาราโอเกะ (YouTube)",
            color = KaraokeColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("พิมพ์ชื่อเพลง, นักร้อง หรือสไตล์เพลง...") },
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
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { onSubmitSearch(query, selectedFilter) }) {
                Text(if (isSearching) "กำลังค้นหา..." else "ค้นหา")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(SearchFilter.entries) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelect(filter) }
                ) {
                    Text(filter.label)
                }
            }
        }

        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Text(
                        text = "ล่าสุด:",
                        color = KaraokeColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
                items(history, key = { it.query }) { item ->
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
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 250.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(KaraokeColors.Background)
        ) {
            items(results, key = { it.videoId }) { song ->
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

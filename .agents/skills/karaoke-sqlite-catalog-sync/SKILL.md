---
name: karaoke-sqlite-catalog-sync
description: >-
  Update and sync the Android TV Karaoke SQLite catalog (song_cache), dynamic Artists menu
  (นักร้อง), and dynamic Music Styles menu (สไตล์เพลง) from YouTube, then build, deploy, and
  verify counts on Android TV via adb. Use when the user asks to update artists, styles, or
  sync/expand the SQLite karaoke song database.
---

# Karaoke SQLite Catalog, Artists & Styles Sync

Workflow for maintaining and updating the `KaraokeTV` Room SQLite database (`karaoke_tv.db`), dynamic **นักร้อง (Artists)** menu, and **สไตล์เพลง (Music Styles)** menu.

## 1. Key Project Files
- **DAO & Aggregations:** `app/src/main/java/com/karaoketv/app/data/local/KaraokeDao.kt`
  - `observeArtistCounts()`: `SELECT artistName AS name, MAX(styleTag) AS styleHint, COUNT(*) AS songCount FROM song_cache GROUP BY artistName ORDER BY songCount DESC LIMIT 60`
  - `observeStyleCounts()`: `SELECT styleTag AS name, COUNT(*) AS songCount FROM song_cache GROUP BY styleTag ORDER BY songCount DESC`
  - `getSongsByArtist(artistName)` / `getSongsByStyle(styleTag)` for instant offline SQLite filtering
- **Repository & Seed Loader:** `app/src/main/java/com/karaoketv/app/data/repository/YouTubeRepository.kt`
  - `seedBundledCatalogIfNeeded(context)`: Loads `app/src/main/assets/karaoke_seed.json` into `song_cache` in batches of 500 when `countCachedSongs()` is below the target threshold.
  - `syncCatalogFromYouTube()`: Fetches fresh YouTube karaoke results (`ytInitialData` or YouTube Data API v3) in parallel and inserts them into `song_cache`.
  - `observeDynamicArtists()` & `observeDynamicStyles()`: Map SQLite aggregation rows to `ArtistProfile` and `MusicStyle` with live `songCount`.
- **UI Screens:** `app/src/main/java/com/karaoketv/app/ui/screens/BrowseScreens.kt` (`ArtistsScreen`, `StylesScreen`, `SongCatalogScreen`) and `app/src/main/java/com/karaoketv/app/MainActivity.kt`.

## 2. Updating Artists & Styles Menus from SQLite
1. Ensure `KaraokeDao` exposes `observeArtistCounts()` and `observeStyleCounts()` over `song_cache`.
2. Ensure `KaraokeViewModel` collects `dynamicArtists` and `dynamicStyles` as `StateFlow`s and routes `browseByArtist(name)` / `browseByStyle(styleTag)` to query SQLite first before falling back to YouTube search.
3. If expanding `app/src/main/assets/karaoke_seed.json`:
   - Fetch unique `videoId` entries from YouTube search (`ytInitialData` `videoRenderer` nodes), assigning each a `styleTag` (`สตริง / ป๊อป`, `ร็อก`, `ลูกทุ่ง / หมอลำ`, `ยุค 90s`, `เพื่อชีวิต`, `สากล (Inter)`, etc.).
   - Update the threshold in `YouTubeRepository.hasCachedCatalog()` and `seedBundledCatalogIfNeeded()` to match the new target count so existing devices automatically import the new songs.

## 3. Build, Deploy & Verify on Android TV
1. Build debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
2. Install and launch on the target Android TV (`<TV_IP>:5555`):
   ```bash
   adb connect <TV_IP>:5555
   adb -s <TV_IP>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
   adb -s <TV_IP>:5555 shell am start -S -n com.karaoketv.app/.MainActivity
   ```
3. Verify SQLite counts on device (`song_cache`, distinct artists, and style breakdown):
   ```bash
   adb -s <TV_IP>:5555 shell "run-as com.karaoketv.app sqlite3 databases/karaoke_tv.db 'SELECT COUNT(*) FROM song_cache; SELECT COUNT(DISTINCT artistName) FROM song_cache; SELECT styleTag, COUNT(*) FROM song_cache GROUP BY styleTag ORDER BY COUNT(*) DESC;'"
   ```

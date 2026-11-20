---
name: karaoke-artist-youtube-fetch
description: >-
  Fetch all available YouTube karaoke songs for any target artist (modeled on the เบิร์ด กะ ฮาร์ท
  multi-query workflow), deduplicate by videoId, assign unique non-reusable 5-digit songCodes,
  tag styleTag and eraTag, update karaoke_seed.json, bump seed_version, and verify in SQLite
  on Android TV. Use when the user asks to add all songs for a specific artist or group of artists.
---

# Artist YouTube Karaoke Fetch & Seed Update

Reusable workflow for adding all available YouTube karaoke tracks for an artist into `Karaoke Now` (`com.karaoketv.app`), modeled on the **เบิร์ด กะ ฮาร์ท** (106-song) ingestion run.

## 1. Multi-Query YouTube Search Strategy
Single-query search (`"{artist} คาราโอเกะ"`) returns ~20 results. To capture an artist's full catalog (50–100+ songs), run parallel queries across:
1. **Spelling & language variants:** e.g., `"เบิร์ด กะ ฮาร์ท คาราโอเกะ"`, `"เบิร์ดกะฮาร์ท คาราโอเกะ"`, `"เบิร์ดกับฮาร์ท คาราโอเกะ"`, `"Byrd & Heart คาราโอเกะ"` (or `"karaoke"` for international artists).
2. **Signature hit titles + artist:** e.g., `"ลืม เบิร์ดกะฮาร์ท คาราโอเกะ"`, `"ไม่ลืม เบิร์ดกะฮาร์ท คาราโอเกะ"`, `"ห่างไกล เบิร์ดกะฮาร์ท คาราโอเกะ"`, etc.

Fetch YouTube search HTML (`https://www.youtube.com/results?search_query=...`), extract `var ytInitialData = ...;</script>`, and walk `videoRenderer` nodes for `videoId`, `title`, and `ownerText`.

## 2. Normalization, Era Classification & Unique Song IDs
When merging results into `app/src/main/assets/karaoke_seed.json`:
1. **Deduplicate by `videoId`:** Skip any `videoId` already in `karaoke_seed.json`.
2. **Normalize `artistName`:** Set a single canonical `artistName` (e.g., `"เบิร์ด กะ ฮาร์ท"`) on both new and existing matching entries so the **นักร้อง** menu groups all songs under one artist card.
3. **Assign `styleTag` and `eraTag`:** Map `eraTag` to one of the 5 standard eras:
   - `ยุคบุกเบิก / ลูกกรุง (2489–2509)`
   - `ยุคราชา-ราชินีลูกทุ่ง (2499–2524)`
   - `ยุคสตริงคอมโบ / เพื่อชีวิต (2514–2534)`
   - `ยุคทอง 90s (2534–2544)`
   - `ยุค 2000s – ปัจจุบัน (2545–2569)`
4. **Never reuse `songCode`:** Find `max_code = max(int(songCode))` across all existing songs and assign `f"{max_code + 1:05d}"` sequentially to newly added songs only.

## 3. Bump Seed Version, Build, Deploy & Verify
1. In `app/src/main/java/com/karaoketv/app/data/repository/YouTubeRepository.kt`, update `hasCachedCatalog()` and `seedBundledCatalogIfNeeded()` with the new total song count and increment `seed_version`.
2. Build and deploy to Android TV:
   ```bash
   ./gradlew assembleDebug
   adb connect <TV_IP>:5555
   adb -s <TV_IP>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
   adb -s <TV_IP>:5555 shell "am start -S -n com.karaoketv.app/.MainActivity && sleep 7"
   ```
3. Verify the exact artist and total counts in SQLite on the TV before confirming:
   ```bash
   adb -s <TV_IP>:5555 shell "run-as com.karaoketv.app sqlite3 databases/karaoke_tv.db \"SELECT COUNT(*) FROM song_cache; SELECT COUNT(*) FROM song_cache WHERE artistName = '<CANONICAL_ARTIST>';\""
   ```

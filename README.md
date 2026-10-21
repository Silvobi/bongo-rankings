# BongoRankings 1.0.1

[Download the latest mod JAR / pobierz najnowszy mod](https://github.com/Silvobi/bongo-rankings/releases/latest).

Server-side Fabric mod for Minecraft Java 26.3. Players do not need a client mod or resource pack.

## Installation / instalacja

Put `BongoRankings-1.0.1.jar` into the server's `mods` directory. Requires Java 25, Fabric Loader 0.19.5+ and Fabric API 0.161.0+26.3. Restart the server, then use `/toplist`. No operator permissions are needed.

Wrzuć `BongoRankings-1.0.1.jar` do folderu `mods` serwera z Fabric API i uruchom serwer ponownie. Gracz otwiera `/toplist`, wybiera statystykę i widzi TOP 10. Przyciski umożliwiają powrót i zamknięcie okna.

## Existing server statistics / dotychczasowa historia

Minecraft statistics are the source for play time, travel distance, crafted/mined/used items, player kills, other entity kills and deaths. Existing offline-player statistics are imported at startup from Minecraft's `LevelResource.PLAYER_STATS_DIR` (26.3: `<world>/players/stats`). Online-player results are read from live vanilla statistics on the server thread. Vanilla files are never modified by this mod.

Czas gry, dystans, crafting, kopanie, użycia przedmiotów, zabójstwa i śmierci pochodzą ze statystyk Minecrafta, również tych zapisanych przed instalacją. Gracze offline uczestniczą w rankingach. Nazwy pochodzą z `usercache.json`, później są zapamiętywane przez mod i aktualizowane przy wejściu. Jeśli starego nicku nie ma w pamięci serwera, widać `Gracz <fragment UUID>` do kolejnego logowania.

| Statistic / statystyka | Source and definition / źródło |
| --- | --- |
| Play time / czas gry | `custom:play_time`; includes AFK like vanilla; ticks converted to hours and minutes, never days or seconds |
| Distance / dystans | Sum of vanilla custom statistics ending `_one_cm`, including walking, swimming, climbing, falling, flight and transport; cm converted to m/km |
| Crafted / stworzone | Sum of `minecraft:crafted`; number of produced items, not number of crafting clicks |
| Mined / wykopane | Sum of `minecraft:mined` |
| Used / zużyte | Sum of `minecraft:used`; vanilla item-use count, not `minecraft:broken` (tools broken) |
| Players killed | `custom:player_kills` |
| Other entities killed / pozostałe byty | `custom:mob_kills`; non-player entities credited to the player by vanilla |
| Deaths / śmierci | `custom:deaths` |
| Placed / postawione | First-install historical estimate: sum of `minecraft:used` for registered `BlockItem` items. Subsequent successful `BlockItem.place` calls are counted exactly, once per successful placement action, including creative. Multi-part blocks count once. Buckets, commands, world generation and arbitrary mod placement methods are not `BlockItem` placements |
| XP gained / zdobyte | Own cumulative counter from installation. Positive `giveExperiencePoints` and standalone positive `giveExperienceLevels`, converted to points |
| XP spent / wydane | Own counter from installation. Negative XP point/level changes and enchanting costs converted to point equivalents. Includes anvil costs. Death loss and administrative `experience set` are excluded |

Minecraft does not store lifetime XP gained/spent or a dedicated historical placed-block counter. Historical block-item use is an estimate, especially with creative or mods; no historical XP is fabricated. XP gained and spent are both cumulative, even if gained XP is later spent. Nested level changes while adding points do not count twice. Administrative `/experience add` also changes counters; `/experience set` only sets the current balance.

Minecraft nie zapisuje sumy zdobytego/wydanego XP ani osobnego historycznego licznika postawień. Postawione bloki sprzed instalacji są przybliżeniem na podstawie użytych przedmiotów blokowych. XP liczy się od instalacji. Wartości XP to punkty, a nie poziomy; koszt 3 poziomów zależy od aktualnego poziomu gracza.

Only positive results are shown. Ties sort by nickname, then UUID. Each board contains at most 10 players. Values use `tys.`/`mln` in Polish and `K`/`M` in English. Hovering over a row shows its unshortened value.

## Language and podium / język i podium

Client locale `pl` / `pl_*` selects Polish. English is the fallback for every other locale and every missing Polish key. Translations are server-rendered from uniquely namespaced resources under `assets/bongo_rankings/lang/`.

First, second and third place use gold, silver and bronze three-stop RGB gradients on the nickname. The server scoreboard's prefix/suffix components are copied separately, preserving BongoCommunityClans colors and tag text. No teams, TAB names or global nickname colors are changed. The mod also works without the clan mod.

## Performance / wydajność

`config/bongo-rankings.json` is generated on first start:

```json
{
  "refreshSeconds": 60,
  "playersPerTick": 1,
  "tickBudgetMicros": 1000
}
```

Restart after editing. Valid ranges: interval 10–86400 seconds, players per tick 1–100, budget 100–10000 microseconds.

The main thread samples one tracked player per tick by default. It stops taking additional players after the soft time budget, so a single unusually large player's statistics may exceed that budget. No worker accesses live Minecraft objects. Immutable snapshots go to one background worker, which imports offline statistics, calculates TOP 10 with bounded heaps, and saves JSON. Refreshes never overlap and `/toplist` reads the published cache; opening the dialog does not scan player files. An existing open dialog is refreshed when reopened. The interval starts after a sampling pass; large player populations therefore add sampling time.

Domyślnie odczyt jest rozłożony po jednym graczu na tick. Import plików offline, sortowanie i zapis odbywają się w tle. Otwieranie okna korzysta z gotowej topki. Nie obiecujemy zerowego kosztu: same dane żywego gracza trzeba bezpiecznie odczytać na głównym wątku, a budżet czasowy jest miękkim limitem między graczami.

## Persistence / zapis

Data: `<world>/bongo-rankings.json`; previous save: `.json.bak`. Writes use a temporary file, fsync and atomic replacement when supported. Data is saved each refresh and during graceful shutdown. Abrupt crashes may lose new custom counter events since the latest successful refresh. If the rankings file is corrupt or has an unknown schema, rankings remain disabled and the original file is preserved. Check logs; restore a valid backup while stopped. Config and data files are separate.

Vanilla statistics are reimported at each server start; offline native statistics are cached during that run. Restart if an administrator externally edits statistics for an offline player. Custom counters remain in the mod data file. Do not remove it if you want to preserve XP and exact new-placement history.

## Build and verification

With JDK 25 set as `JAVA_HOME`:

```powershell
.\gradlew.bat build
```

On Linux/macOS use `./gradlew build`. The mod JAR is written to `build/libs/`.

The source includes unit tests for localization/fallback, formats, XP curves, offline imports, persistence, corrupt-data preservation and TOP 10 ordering. `probeJar` creates a separate isolated-test mod; never install it on a real server. `integrationTest -PtestPort=25589` connects vanilla-protocol test clients to a separately prepared loopback-only test server with fixture players and the probe mod. Test commands in that mod deliberately mutate test-player XP and blocks.

Fabric documentation: https://fabricmc.net/2026/09/15/263.html
Clan integration reference: https://github.com/Silvobi/bongos-community-clans

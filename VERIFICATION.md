# Verification — BongoRankings 1.0.1

## 1.0.1 — immediate dialog close

- `gradlew build`: successful, all 10 existing JUnit tests pass.
- `gradlew integrationTest -PcloseCheck`: successful, 68 assertions on the isolated 26.3 server with BongosCommunityClans 1.0.4.
- Both PL and EN: the menu and each of the 11 ranking dialogs use `DialogAction.CLOSE`. Every exit button has no server action, so vanilla closes locally without sending a custom-click packet or waiting for a server response.
- Back navigation and reopening `/toplist` work after local close. Verified decoded dialog packets against Minecraft 26.3 client `DialogScreen.runAction` behavior; no graphical-client timing measurement was performed.

## Baseline verification from 1.0.0

Date: 2026-10-16. Minecraft Java 26.3, JDK 25.0.1, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3. Isolated loopback server; no production world was modified.

- `gradlew build`: successful; 10 JUnit tests, 0 failures.
- `gradlew integrationTest`: successful; 23 assertions using three vanilla-protocol clients (Polish, English, German fallback) and 12 historical offline-player fixtures.
- `gradlew integrationTest -PrestartCheck`: successful after graceful shutdown and a real restart, with the test probe removed; XP gained, XP spent and placement counters survived.
- Integration server also loaded BongosCommunityClans 1.0.4. Actual clan tag RGB color `#12AB34` survived in dialog rows; nickname received a separate gold gradient.
- Tested all 11 category routes, back/close routes, correct TOP 10 limit, old offline play time displayed as `138 godz. 15 min.`, PL/EN labels and fallback for an unsupported client language.
- Test probe awarded 1395 XP points plus one level at level 30: cumulative gained 1507 XP. Enchant cost one level and subsequent negative one-level change (the same method vanilla anvil costs use) produced 219 XP spent. No nested-level double counting.
- One actual `BlockItem.place` success and an out-of-build-height failure produced a placement counter of exactly 1.
- Unit coverage includes all three podium gradients, preservation of mixed-color clan components, old vanilla-stat aggregation, deterministic TOP 10 ties, English fallback, time/number/distance formatting, XP level boundaries, persistence round trips, first-install placement estimation and corruption preservation.
- Release JAR contains neither the test probe nor the integration client harness. Translation files are namespaced to avoid collisions with other mods.

Scope: dialogs were decoded and their rich text inspected through the vanilla network protocol. A graphical Minecraft client was not used for screenshot-based visual QA. Performance checks verify the bounded sampling/background-worker design; this is not a benchmark against a production server population. The per-player sampling time budget is soft.

Historical limitations: vanilla has no lifetime XP gained/spent history or dedicated placed-block total. Historical placement is estimated from used block items; XP starts at installation. These limitations are documented in README.md.

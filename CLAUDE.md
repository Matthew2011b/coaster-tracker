# Lift Hill

Matthew's coaster credit tracker (Kotlin, Jetpack Compose, sideloaded). Repo and package are still
named coaster-tracker / com.m2011b.coastertracker; the applicationId must not change or updates won't
install over the phone's copy. The UI kit lives in `com.m2011b.kit`. Future apps borrow pieces from
any of Matthew's repos rather than starting from a template.

## Build and test
- `./gradlew testDebugUnitTest`: unit tests plus Robolectric screenshot tests at phone size.
  PNGs land in `app/build/test-out/shots/` (CI uploads them as the `screenshots` artifact).
  Look at them after every UI change, and show them to Matthew before handing over an APK.
  Add a screenshot test per real screen, plus a 360x640 small-phone check.
- `./gradlew assembleRelease`: APK. Unsigned without the key (see Signing).
- CI (`.github/workflows/build.yml`): every push to main tests, builds, signs and uploads the APK and the AAB (for Google Play).
  Tag `vX.Y` to publish a GitHub Release with the APK attached. Tag every version handed over.
- Bump `versionCode` and `versionName` in `app/build.gradle.kts` for every build that goes to a phone.

## Signing (one key per app, never committed)
- Repo secrets: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.
  Locally: gitignored `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword)
  or APP_KEYSTORE / APP_KEYSTORE_PASSWORD / APP_KEY_ALIAS / APP_KEY_PASSWORD env vars.
- Repo variable SIGNING_CERT_SHA256: set after the first signed build. CI then fails any APK signed
  with a different key, because it would not install over the copy on the phone.
- Matthew generates keys on his own PC. Never generate, print or ask for a key or password in chat.

## UI kit (app/src/main/java/com/m2011b/kit)
- `theme/Tokens.kt`: ColorToken (key, label, light, dark, derive) and `Std`. Six core tokens are what
  users edit; tokens with `derive` are worked out from them unless "Advanced colors" is on.
  Token keys are saved on users' phones: never rename a shipped key.
- `theme/ThemeStore.kt`: persisted light/dark palettes, System/Light/Dark mode, the advanced switch,
  theme codes (`THEME1|...`) to copy a look between apps.
- `theme/KitTheme.kt`: KitTheme(store, fonts) maps tokens onto Material so stock widgets follow.
  Read colors with `LocalPalette.current` (`p.accent`, `p[AppTokens.X]`), fonts with `LocalKitFonts`.
- `theme/ThemeEditor.kt`, `theme/ColorPicker.kt`: the Custom colors screen (Settings > "+").
- `ui/AppShell.kt`: KitAppShell = TrackBar on top, bottom nav (NavItem icons are SVG path strings on a
  24 grid), back handling. `ui/TrackBar.kt`: 56dp bar with coaster track along the bottom (dip, bunny
  hill, 49-degree lift hill matching the logo, straight under the title, loop at the right), a train
  that rolls to the current tab's stop, Title Case title centered over the straight, back arrow at the
  left, optional BarAction in the loop. Track color is the HeaderTrack token (logo orange by default).
- `ui/Components.kt`: FieldBox (status-colored input box), FieldLabel, SelectBox, PrimaryButton,
  SecondaryButton, Panel, StatTile, Segmented, KitDialog (text only), FormDialog.

## Rules learned the hard way
- Dialogs with input boxes use FormDialog, never AlertDialog: AlertDialog plus full-width fields
  locked up layout at real phone widths (only reproduced at 400dp+, so test at real sizes).
- Every color comes from a token. No hardcoded colors in screens; add a token instead.
- Main screens should fit one phone screen without page scrolling; long lists scroll inside a box.
- Any SQLite schema change needs an onUpgrade step and a test that starts from the old schema.

## App-specific code (com.m2011b.coastertracker)
- `data/`: Db (SQLite v2, seeded from assets/snapshot.json; v2 added parks.city/state), Repo (state + actions), Stats (all screen math),
  Rules (SBNO 9-month clock, kiddie/carnival/mountain filters, what counts), Normalizer (assets/normalize.json aliases and
  model merges), Sync (Coasterpedia pull with the user's own API key), Csv (import/export).
- Data rules: make/model never change on updates (only blanks fill); user edits live in `overrides`;
  logged rides always count, whatever the filters; a coaster is SBNO only after 270 days standing.
  Three separate filter switches (all off by default): kiddie (kiddie model list, or under ~20 ft and under
  ~25 mph or no speed), carnival (Coasterpedia location_kind travelling), mountain (normalize.json alpineModels
  + name rule). Rules.filteredOut is the single check. The log screen hides filtered coasters and parks left empty.
  Distances: Units.metric (Settings "Distances in kilometers"); miles() formats per that setting.
  App updates: Db.mergeBundled merges a newer assets/snapshot.json ("generated" date > meta data_as_of) into
  existing installs with the same rules as Sync; bump "generated" when shipping new data.
  US parks show "City, ST": state comes from /v1/parks, city only from /v1/parks/{id} (snapshot only).
  Park coordinates: Coasterpedia lacks them for ~670 parks; filled from OpenStreetMap/Wikidata/hand lookups
  (tools/snapshot/coords, about 200 placed at their town center). Sync keeps stored coordinates when Coasterpedia
  sends none.
- Screens: LogScreen.kt, Screens.kt (Stats, Model detail, Parks, Park detail, Nearest), ParksMap.kt (map view,
  toggled by the loop button on Parks) drawing Basemap.kt / assets/basemap.bin: Natural Earth (public domain) land,
  lakes, rivers, borders, interstates and towns, bundled so the map needs no servers. Rebuild the asset with
  tools/basemap/build_basemap.py; check it with tools/basemap/preview.py), SettingsScreen.kt.
- Log a Ride has two modes (loop button, remembered in prefs quick_log): the picker, and a quick list of 3-line tiles
  (name, manufacturer, model) sized to fit a big park without scrolling; tap logs with an Undo bar.
- Model and maker pages: loop map button shows Stats.installParks on ParksMap (fitAll frames the nearest few).
- Completion: Repo.log returns Milestones (data/Stats.kt milestones()) and queues them in repo.celebrations; App shows
  CelebrationDialog (Celebrate.kt): ticket = park, builder's plate = maker (steel US / brass world), blueprint = model.
  Models and makers need 2+ installations; world beats US when both finish at once; US-only users get US cards only.
  The card dialog has Share (the link) and Close; the old image share was dropped in 0.22 (FileProvider entry left
  in the manifest, unused). The plate and blueprint colors are fixed
  artwork constants (like the logo), as are the ticket colors, the one exception to the token rule.
- Wishlist: stars on coasters (park detail, model installations) and models (model panel), stored in prefs "wish"
  (c:<id>, m:<make|model>). Starring a model stars its installations (tracked as "a:<id>"); unstarring it removes only those. Wished parks get a
  Wish-yellow ring and a twinkling star on the map; starred models show a yellow star in Nearest but keep their
  distance order (Nearest is always nearest first). Stats has a search box (Stats.search: word-start matches).
- Sharing: SHARE sends just the link (Share.kt ShareLinks; ids and numbers only, names come from web/public/names.json
  because chat apps mangle "&" in links). Site = web/ on Cloudflare Workers (free plan, 10 ms CPU): build-images.mjs pre-draws a
  1200x630 JPEG for every park, model and maker (US/world) in snapshot.json; src/worker.js serves pages with og tags
  (title = the name; the card says 100% Complete) pointing at those images, falling back to generic art; the page draws the visitor's own
  card in the browser with public/card.js. .github/workflows/share.yml redraws and deploys on changes to web/ or the
  snapshot (secrets CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID). Site: https://lift-hill.lifthill.workers.dev.
  ShareLinks.slug() must match slug() in web/src/card.js. Set PLAY in worker.js once the app is on the Play Store.
  Privacy policy (Play listing link): /privacy, from web/src/privacy.js. Update it when the app sends anything new off the phone.
- Settings: two rows open Colors & Theme (ColorsScreen: presets, Custom = ThemeEditorScreen, System/Light/Dark) and
  Backup & Data (BackupScreen.kt); then the filter toggles and the Coasterpedia key panel (key and Update data on one row,
  "Get a free key" left, data date right). Tuned to fit one screen on Matthew's phone (ScreenshotTest.settingsOnPhone).
- Backup: data/Backup.kt is the JSON format (rides, overrides, custom coasters, wishes, prefs, theme code). Repo.restoreBackup
  merges, never replaces: union of rides (earliest date wins), local edits win, custom coasters matched by name + park and
  re-id'd, stars combined, settings and theme from the backup. Drive.kt (DriveBackup) = Identity AuthorizationClient with
  drive.appdata scope + Drive REST v3 on one file (lift-hill-backup.json) in appDataFolder. Auto backup runs from
  MainActivity.onStop when Repo.changedAt > backup_at. Google Cloud project lift-hill-app (mrbsoftware.dev account), Android
  OAuth client registered with the release key's SHA-1; the app is in Testing, so only listed test users can sign in.
  The Sign in with Google button uses Google's fixed brand colors (another token-rule exception).
- Coaster data is Coasterpedia (CC BY-SA 4.0): keep the credit line on Settings.
- Building here: Maven Central rate-limits shared IPs; a local Gradle init script points at Google's mirror.

## Working with Matthew
- Direct and concise. No em dashes in user-facing text.
- New UI: settle the look on a Claude Design canvas or HTML prototype first, then build.
- He reviews screenshots, then gets the APK from GitHub (Actions artifact or Release).

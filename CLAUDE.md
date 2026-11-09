# Coaster Tracker

Template for Matthew's Android apps (Kotlin, Jetpack Compose, sideloaded). New apps start as a copy
of this repo; `tools/new-app.sh` renames it. The UI kit lives in `com.m2011b.kit` and is shared by
every app built from here: keep it self-contained so it can be copied into older apps too.

## Build and test
- `./gradlew testDebugUnitTest`: unit tests plus Robolectric screenshot tests at phone size.
  PNGs land in `app/build/test-out/shots/` (CI uploads them as the `screenshots` artifact).
  Look at them after every UI change, and show them to Matthew before handing over an APK.
  Add a screenshot test per real screen, plus a 360x640 small-phone check.
- `./gradlew assembleRelease`: APK. Unsigned without the key (see Signing).
- CI (`.github/workflows/build.yml`): every push to main tests, builds, signs and uploads the APK.
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
- `theme/Tokens.kt`: ColorToken (key, label, light, dark) and `Std`, the standard tokens.
  Token keys are saved on users' phones: never rename a shipped key.
- `theme/ThemeStore.kt`: persisted light/dark palettes, System/Light/Dark mode, theme codes
  (`THEME1|...`) to copy a look between apps.
- `theme/KitTheme.kt`: KitTheme(store, fonts) maps tokens onto Material so stock widgets follow.
  Read colors with `LocalPalette.current` (`p.accent`, `p[AppTokens.X]`), fonts with `LocalKitFonts`.
- `theme/ThemeEditor.kt`, `theme/ColorPicker.kt`: the hidden theme menu.
- `theme/SecretTaps.kt`: `Modifier.secretTaps(count) { }` for hidden unlocks.
- `ui/AppShell.kt`: KitAppShell = top bar, accent rule, side menu, back handling, and the hidden
  theme menu on 5 taps below the menu items. Use it for every app unless told otherwise.
- `ui/Components.kt`: FieldBox (status-colored input box), FieldLabel, SelectBox, PrimaryButton,
  SecondaryButton, Panel, StatTile, Segmented, KitDialog (text only), FormDialog.

## Rules learned the hard way
- Dialogs with input boxes use FormDialog, never AlertDialog: AlertDialog plus full-width fields
  locked up layout at real phone widths (only reproduced at 400dp+, so test at real sizes).
- Every color comes from a token. No hardcoded colors in screens; add a token instead.
- Main screens should fit one phone screen without page scrolling; long lists scroll inside a box.
- Any SQLite schema change needs an onUpgrade step and a test that starts from the old schema.

## App-specific code (com.m2011b.coastertracker)
- `data/`: Db (SQLite v1, seeded from assets/snapshot.json), Repo (state + actions), Stats (all screen math),
  Rules (SBNO 9-month clock, kiddie/carnival filter, what counts), Normalizer (assets/normalize.json aliases and
  model merges), Sync (Coasterpedia pull with the user's own API key), Csv (import/export).
- Data rules: make/model never change on updates (only blanks fill); user edits live in `overrides`;
  logged rides always count, whatever the filters; a coaster is SBNO only after 270 days standing.
- Screens: LogScreen.kt, Screens.kt (Stats, Model detail, Parks, Park detail, Nearest), SettingsScreen.kt.
- Coaster data is Coasterpedia (CC BY-SA 4.0): keep the credit line on Settings.
- Building here: Maven Central rate-limits shared IPs; a local Gradle init script points at Google's mirror.

## Working with Matthew
- Direct and concise. No em dashes in user-facing text.
- New UI: settle the look on a Claude Design canvas or HTML prototype first, then build.
- He reviews screenshots, then gets the APK from GitHub (Actions artifact or Release).

# Android Starter

Template for new Android apps: Kotlin + Jetpack Compose, a shared UI kit with a hidden theme menu,
screenshot tests, and a GitHub Actions build that signs the APK and checks the key.

## New app checklist
1. On GitHub: create an empty private repo for the app.
2. Ask Claude to "start <app name> from android-starter in <repo>". It copies this template and runs
   `tools/new-app.sh com.m2011b.<name> "<App Name>"`.
3. Make a signing key for the app on your PC (one key per app). In PowerShell, using the keytool
   that ships with Android Studio:
   ```
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore myapp.keystore -alias myapp -keyalg RSA -keysize 4096 -validity 10000
   ```
   Pick a strong password (a password manager's generator works) and save it there.
   Back up `myapp.keystore` somewhere safe. Losing it means updates can never install over the app.
4. Copy the key as text for GitHub:
   ```
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("myapp.keystore")) | Set-Clipboard
   ```
5. In the app's repo: Settings > Secrets and variables > Actions > New repository secret:
   `KEYSTORE_BASE64` (paste), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`myapp`), `KEY_PASSWORD`
   (same as the store password unless you set a different one).
6. After the first signed build, copy the "Signing cert" value from the build log into a repo
   **variable** named `SIGNING_CERT_SHA256`. From then on CI rejects APKs signed with any other key.

## Getting builds
- Every push to main: Actions tab > latest run > Artifacts.
- Tagged versions (`v1.0`, `v1.1`...): the Releases page.

## The hidden theme menu
Open the side menu and tap the empty space under the items 5 times quickly. Every color in the app
is editable for light and dark mode, and "Copy theme code" / "Paste code" move a look between apps.

Fonts: IBM Plex Sans/Mono, SIL Open Font License (FONTS-OFL.txt).

# One Breath — development guide

English | [繁體中文](DEVELOPMENT.md) · [Back to the README](../README.en.md)

This guide is for people who want to build, change or contribute to the app. The product overview and the everyday install steps are in the [README](../README.en.md).

## Requirements
| Item | Value | Source |
| --- | --- | --- |
| minSdk | 26 (Android 8.0) | `app/build.gradle` |
| targetSdk / compileSdk | 34 | `app/build.gradle` |
| Java | 17 (`sourceCompatibility` / `targetCompatibility`) | `app/build.gradle` |
| Android Gradle Plugin | 8.7.3 | `build.gradle` |
| Gradle | 8.11.1 (same for the wrapper and CI) | `gradle/wrapper/gradle-wrapper.properties`, `.github/workflows/main.yml` |
| Version | versionName `2.1`, versionCode `4` | `app/build.gradle` |

- Plain Java with XML layouts: no Compose, no third-party libraries (`dependencies` is empty), and no AndroidX or AppCompat either (`android.useAndroidX=false` in `gradle.properties`).
- Please think twice before adding a dependency: the app deliberately uses only built-in Android APIs.

## Building locally
You need the Android SDK. Point to it with the `ANDROID_HOME` environment variable, or with `sdk.dir=...` in `local.properties` at the project root (that file is in `.gitignore`).

```sh
./gradlew assembleDebug      # Windows: .\gradlew.bat assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`.

Release builds: set the environment variables `KEYSTORE_FILE` (path to the `.jks` file) and `KEYSTORE_PASSWORD`, then run `assembleRelease`. If they aren't set, or `KEYSTORE_FILE` points to a file that doesn't exist, the release APK is built **unsigned** and can't be installed as is.

## Building with GitHub Actions (no Android Studio needed)
1. Sign up for / log in to GitHub, click "+" in the top-right corner → "New repository", and create a new repo (Private is fine)
2. On the repo page, click "uploading an existing file" and drag **all files and folders** in the project folder into it to upload
   - Make sure the `.github` folder gets uploaded (it's a hidden folder: on Windows turn on "Show hidden items", on Mac press Cmd+Shift+.)
3. Set up the signing key (only needed once; see "Release signing" below). **Without it, the build fails straight away.**
4. Open the "Actions" tab at the top; you'll see "Build APK" running, which takes about 3–5 minutes
5. When it finishes (green check mark), open it; at the bottom under "Artifacts" there's `OneBreath-apk` — download and unzip it to get the signed release APK

### What the workflow does
`.github/workflows/main.yml` (named "Build APK"):

- **Triggers**: every push, plus manual runs from the Actions tab (`workflow_dispatch`).
- Runs on `ubuntu-latest` with Temurin JDK 17, and installs Gradle 8.11.1 through `gradle/actions/setup-gradle`. CI calls `gradle` directly, not `./gradlew`.
- **Restore signing key**: decodes the `KEYSTORE_BASE64` secret into `$RUNNER_TEMP/release.jks` and sets `KEYSTORE_FILE`. If either secret is missing, this step stops with an error.
- **Build signed release APK**: finds the folder containing `settings.gradle` and runs `gradle assembleRelease` there.
- Uploads `**/build/outputs/apk/release/*.apk` as an artifact named `OneBreath-apk`; the step fails if no APK is found. Artifacts can only be downloaded by signed-in GitHub users and are deleted automatically once the repository's retention period runs out.
- The workflow doesn't create a GitHub Release or publish to any store.

## Release signing
GitHub Actions builds the APK as a **release** build and signs it with the same key every time, so future versions can be installed straight over the old one and settings are kept. The key isn't stored in the repo; it lives in GitHub Secrets:

Repo page → Settings → Secrets and variables → Actions → New repository secret, and add these two:

| Name | Value |
| --- | --- |
| `KEYSTORE_BASE64` | The keystore file (`.jks`) encoded as base64 text |
| `KEYSTORE_PASSWORD` | The keystore password |

- The key alias is always `onebreath`, and the same `KEYSTORE_PASSWORD` is used as both the store password and the key password (see `signingConfigs` in `app/build.gradle`).
- Keep your own backup of the keystore file and password: if you lose them, APKs you build later can't be installed over the old version (you'll have to uninstall first).
- The first time you switch from a debug build to the release build, the signatures differ, so uninstall the old version on the phone first.

## Permissions
`AndroidManifest.xml` has **no `<uses-permission>` entries at all**, so there's no internet permission. The only things declared are:

- The accessibility service `PauseService` (`android.permission.BIND_ACCESSIBILITY_SERVICE` — this protects the service binding for the system; it isn't a permission requested from the user, who has to switch the service on in system settings).
- A `<queries>` entry for apps with a launcher activity (`MAIN` + `LAUNCHER`), which Android 11+ requires before the group editor can list installed apps.
- Screen on/off broadcasts (`SCREEN_OFF`, `SCREEN_ON`, `USER_PRESENT`), registered at runtime inside the service, with `RECEIVER_NOT_EXPORTED` on Android 13+.

Note that `android:allowBackup="true"` is set, so if the user has Android's system backup turned on, the SharedPreferences (settings, groups, counts) may be included in it. The app itself never sends anything anywhere.

## How it works

### Accessibility service (`service/PauseService.java`)
- Configured in `res/xml/accessibility_config.xml`: listens for `typeWindowStateChanged|typeWindowContentChanged`, with `flagReportViewIds`, `canRetrieveWindowContent="true"` and a 100 ms `notificationTimeout`.
- The "current target" is either an app (its package name) or a website (`web:<domain>`).
- `TYPE_WINDOW_STATE_CHANGED` means the foreground app may have changed. System UI (`com.android.systemui`, permission dialogs, Samsung's edge panel and so on — see `IGNORE`), keyboards and toasts don't count as switching apps.
- While One Breath's own screens are in front (the pause screen or the settings), the current target's timer is paused, but the session isn't treated as finished.
- When you switch into a target: if its group has "Breathe before opening" on and the target isn't in its grace period, `PauseActivity` opens in open mode; otherwise `Sessions` starts timing straight away. If the system refuses to show the screen, the app lets you through silently rather than getting in your way.
- Screen off stops the timers, so no reminder pops up on a dark screen. After unlocking, timing resumes without showing the open screen again.
- Every exception while handling events is swallowed: if the service crashes, the system turns it off, and missing one event is the lesser evil.

### Browser address bar (`service/BrowserUrlReader.java`)
- Only read when a known browser is in front **and** at least one enabled group could match a website. `TYPE_WINDOW_CONTENT_CHANGED` fires constantly, so reads are throttled to one every 400 ms, with one guaranteed read after the last change.
- The address bar is found by view id only (`findAccessibilityNodeInfosByViewId`); nothing else on screen is read. If the bar has focus (the user is typing) or is only showing its hint text, the result counts as "unknown" and the current state is kept.
- Supported browsers (package → address-bar id): Chrome (including Beta / Dev / Canary), Brave, Edge, Vivaldi, Kiwi, Opera, Opera Mini, Samsung Internet (including Beta), Firefox (including Beta and Nightly, `org.mozilla.fenix`), Firefox Focus and DuckDuckGo. To support another browser, add a line to `URL_BAR_IDS`.
- If the browser itself is in a group and its own open screen is about to appear, the app rule wins.

### In-use reminder timing (`service/Sessions.java`)
- State lives in memory only (shared across the process); if the system kills the process, counting starts over.
- Only time with the target in the foreground is counted. Coming back within the "quiet time after Continue" minutes continues the same session; after that it starts fresh.
- When the timer is due, the connected accessibility service opens `PauseActivity` in reminder mode with the minutes used so far. The next round is scheduled first, so a screen that fails to appear can't cause a burst of repeats.
- Choosing to keep going on the reminder screen restarts the interval but keeps the total time used.
- Backing out (the button, the Back key, or leaving the screen) clears that target's grace period and timer, and adds one to "chose not to open".
- All timers stop when the service is turned off.

### Pause screen (`ui/PauseActivity.java`, `ui/PauseOrbView.java`)
- Two modes: before opening (`MODE_OPEN`) and in-use reminder (`MODE_REMIND`, warm colours). It's `singleTask`, has its own `taskAffinity` and is excluded from Recents.
- The wait comes from the target's rule (the group's custom timing or the global settings) and is timed with `elapsedRealtime` rather than the animation, so it stays accurate when system animations are off. The continue button only fades in once the wait is over and can't be tapped before then.
- Each time open mode appears, both the per-target count and today's total go up by one.
- Back = give up and go to the home screen. Pressing Home, switching to another app or turning the screen off (`onStop`) is also treated as giving up: the screen closes, it counts as one "chose not to open", and the next launch asks again.
- Continue starts the grace period (`Prefs.startGrace`); in open mode it also launches the app for you.
- The breathing orb is a custom-drawn View: 4 seconds in, 4 seconds out, with no countdown or progress shown on purpose. If the cue words are too long for the orb (as in English), the text is scaled down to fit.

### Data and settings (`data/`)
- Everything is stored in the SharedPreferences file `settings`:
  - Global settings: `enabled` (master switch), `delay_seconds` (default 5, 0–30), `grace_minutes` (default 5, 1–30), `remind_minutes` (default 10, 1–60), `remind_delay_seconds` (default 5, 0–30), `language` (`system` / `zh` / `en`).
  - Grace periods: `grace_until_<target>`. If the phone's clock is turned back, the grace period can't become absurdly long.
  - Daily counts: `count_day` (`yyyyMMdd`, the phone's local time), `today_total`, `today_resisted`, `cnt_<target>`. They reset automatically when the date changes.
  - Groups: `groups_v2`, a JSON array (`id`, `name`, `enabled`, `pauseOnOpen`, `remindInUse`, `blockWeb`, `customTiming`, `delaySeconds`, `remindMinutes`, `remindDelaySeconds`, `apps`, `sites`).
- There's one shared `GroupStore` for the whole app, with an in-memory cache and prebuilt lookup tables, because the service queries it on every event.
- An app can only be in one group: saving removes it from other groups, and duplicates are dropped on load.
- New groups start with all three features on and the global timing (the timing fields are prefilled from the current global settings).
- A `Rule` is the effective setting for a target. Without custom timing, the global settings are re-read every time.
- Website matching checks a group's custom websites first, then the built-in `SiteCatalog` table (about 30 common apps, e.g. X → x.com, twitter.com). All domains of one app map to one "canonical" domain (the first one), so switching between them doesn't trigger again. Subdomains match too (`m.x.com` matches `x.com`). `SiteCatalog.normalize` strips the scheme, path, port and a leading `www.`; text that isn't a URL (search terms, `about:blank` and so on) is ignored.
- Upgrading from v1: v1 only had a single app list (`apps`). On first load, if there are no groups yet, it's moved into a new group named “My apps” / 「我的 App」 in the interface language active at that moment, and the old key is removed. From then on the name is user data and doesn't change with the language.

### Screens (`ui/`)
- `MainActivity` holds the three tabs (Home / Groups / Settings) via `<include>`, each driven by `HomePage`, `GroupsPage` and `SettingsPage`; the current tab is refreshed on tab switch and in `onResume`. The current tab is kept in `onSaveInstanceState`, so after rotation or a language change you stay on the same tab.
- The "Enable reminders" master switch sits at the bottom of Settings. Home re-reads it every time it's shown and notes on the group summary when reminders are paused.
- `GroupEditActivity` has a two-part "Settings | Apps" switch at the top. The Apps part is a `ListView` that scrolls on its own (there can be two or three hundred apps), and the app list loads on a background thread. Nothing is written until you tap Save; with unsaved changes, Back asks what to do first. Edits in progress survive rotation.

### Languages (`data/AppLocale.java`)
- English lives in `res/values/strings.xml` (the default) and Traditional Chinese in `res/values-zh-rTW/strings.xml`. Both files must have exactly the same keys and matching format arguments (`%1$s`, `%1$d`); English uses `<plurals>` where it needs singular and plural forms.
- **Why `values-zh-rTW` and not `values-zh`**: on Android 7 and later, a region-less `values-zh` is treated as Simplified Chinese (zh-Hans), so Traditional locales (zh-Hant, e.g. zh-TW) don't match it and fall back to English. Chinese therefore lives only in `values-zh-rTW`, and the app doesn't rely on the system's matching:
  - "繁體中文" applies zh-TW.
  - "English" applies `en`.
  - "System" applies zh-TW when the system's **first** language is any kind of Chinese (zh-TW, zh-HK, zh-CN…), and otherwise leaves the configuration alone (→ English). Only the first language is checked: a system list of "Japanese, Chinese" gives English.
- How it's applied: `AppLocale.wrap(Context)` re-reads Prefs on every call and wraps the context using `Configuration.setLocale` plus `createConfigurationContext` (both available since API 17, so no version check is needed with minSdk 26). No AppCompat.
  - All three activities (`MainActivity`, `GroupEditActivity`, `PauseActivity`) call it from `attachBaseContext`.
  - Non-activity code that needs strings must wrap its context too — for example `GroupStore` when it names the migrated v1 group.
  - The accessibility service itself resolves no strings and builds no UI; the pause screen re-reads the language every time it's created, so a language change needs no service restart or reboot.
  - After a language change in Settings, the activity calls `recreate()` and stays on the Settings tab.
- Not affected by the in-app setting: the launcher name (`app_name`) and the service's name and description in the system's accessibility settings (`service_description`) are resolved by the system using the system language. With a Simplified Chinese (zh-CN) system language, the system can't match `values-zh-rTW`, so these may appear in English.

## Project structure
`app/src/main/java/com/onebreath/app/`
- `data/`: data and settings
  - `Prefs.java`: global settings (including the interface language), the quiet period after "Continue", daily counts
  - `AppLocale.java`: applies the language setting (System / Traditional Chinese / English) to activities and to any other code that needs strings
  - `Group.java`, `GroupStore.java`: groups and their storage (kept in the phone's SharedPreferences)
  - `Rule.java`: the settings that finally apply to a given app or website
  - `SiteCatalog.java`: the built-in app → website mapping and URL normalisation
- `service/`: background detection
  - `PauseService.java`: the accessibility service; detects which app you switch to and times the in-use reminder
  - `BrowserUrlReader.java`: reads only the browser's address bar to recognise websites that should trigger a reminder
  - `Sessions.java`: in-use reminder timing, and what happens after the user continues or backs out
- `ui/`: screens
  - `PauseActivity.java`, `PauseOrbView.java`: the deep-breathing screen and breathing animation
  - `MainActivity.java`: the main screen and bottom navigation bar; `HomePage.java`, `GroupsPage.java`, `SettingsPage.java` are its three tabs
  - `GroupEditActivity.java`, `AppListAdapter.java`: group editing and app picking
  - `SeekRow.java`: the slider row shared by Settings and the group editor (a SeekBar plus its live value, e.g. "5 seconds", "Every 10 minutes")
  - `UiKit.java`: small shared helpers: dp conversion, checking whether the accessibility service is on, the feature chips on group cards, segmented-control styling, and making a whole row toggle its switch

`app/src/main/res/`
- `values/strings.xml`: English interface text (the default)
- `values-zh-rTW/strings.xml`: Traditional Chinese interface text
- `xml/accessibility_config.xml`: accessibility service configuration
- `layout/`: XML layouts for every screen

Also:
- `.github/workflows/main.yml`: the GitHub Actions build (see above)
- `app/build.gradle`: version, SDK levels, signing configuration

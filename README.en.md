# 喘口氣（One Breath）

English | [繁體中文](README.md)

A home-made take on one sec: when you open a distracting app you've picked, a deep-breathing screen pops up first. After a short wait you can either continue to the app or choose "Never mind, I'll skip it". Nothing is ever locked — if you really need the app, a few seconds' wait gets you in.

## Features
- **Breathe before opening**: before a distracting app opens, you get a breathing animation plus a reflective question, along with how many times you've opened it today
- **In-use reminder**: if you stay in the same app, it asks again every N minutes of use (1–60 minutes) whether you want to keep going; this reminder screen has its own wait time
- **Groups**: sort apps into groups (e.g. "Social", "Video", "Games"). Each group decides for itself whether to use "Breathe before opening", "In-use reminder" and "Also remind on their websites", and can either follow the global timing or use its own. An app can belong to only one group
- **Website reminder**: if you pick an app that has a website (e.g. X → x.com, twitter.com), opening that site in a browser triggers a reminder too; you can also add custom websites with no matching app to a group (e.g. `news.example.com`)
- Adjustable wait time (0–30 seconds, 0 = no wait); the screen deliberately shows no countdown or progress, so you aren't just staring at the clock
- After you continue, no reminders for N minutes (1–30 minutes), so you don't get interrupted over and over
- The Home tab shows how many breaths you've taken today and how many times you chose not to open the app; one tap at the bottom of Settings pauses all reminders
- **Interface language**: English and Traditional Chinese. In Settings, choose "System" (the default: any Chinese phone language shows Traditional Chinese, every other language shows English), "繁體中文" or "English"; the change applies immediately. The app name on the home screen and the description in the phone's accessibility settings follow the phone's system language, not this setting
- **Privacy**: no internet permission, no ads; all settings and counts stay on your phone and are never sent anywhere. The accessibility permission is used only to know which app is in the foreground (the app name). In browsers, only the URL in the address bar is read, to recognise the websites you picked — nothing else on screen is ever read

## Screens
- **Home**: accessibility permission status (with step-by-step help if it's off), today's counts, and a summary of your groups
- **Groups**: one card per group (app icons, which features are on, a group on/off switch); tap a card to edit its name, features, timing and custom websites, and use the "Apps" tab to search for and tick the apps to put in that group
- **Settings**: global timing (wait before opening, quiet time after "Continue", in-use reminder interval and the wait on the reminder screen), language (System / 繁體中文 / English), the About & privacy notes, and at the very bottom the "Enable reminders" master switch

## Getting the APK (no Android Studio needed)
1. Sign up for / log in to GitHub, click "+" in the top-right corner → "New repository", and create a new repo (Private is fine)
2. On the repo page, click "uploading an existing file" and drag **all files and folders** in this folder into it to upload
   - Make sure the `.github` folder gets uploaded (it's a hidden folder: on Windows turn on "Show hidden items", on Mac press Cmd+Shift+.)
3. Set up the signing key (only needed once; see "Release signing" below)
4. Open the "Actions" tab at the top; you'll see "Build APK" running, which takes about 3–5 minutes
5. When it finishes (green check mark), open it; at the bottom under "Artifacts" there's `OneBreath-apk` — download and unzip it to get the signed release APK

## Release signing
GitHub Actions builds the APK as a **release** build and signs it with the same key every time, so future versions can be installed straight over the old one and your settings are kept. The key isn't stored in the repo; it lives in GitHub Secrets:

Repo page → Settings → Secrets and variables → Actions → New repository secret, and add these two:

| Name | Value |
| --- | --- |
| `KEYSTORE_BASE64` | The keystore file (`.jks`) encoded as base64 text |
| `KEYSTORE_PASSWORD` | The keystore password |

Keep your own backup of the keystore file and password: if you lose them, APKs you build later can't be installed over the old version (you'll have to uninstall first). The key alias is always `onebreath`.

The first time you switch from a debug build to the release build, the signatures differ, so uninstall the old version on your phone first.

## Installing on a Samsung phone
1. Copy the APK to your phone and tap it to install (the first time, you'll be asked to allow "Install unknown apps")
2. Open One Breath and, on the Home tab, tap **① Turn on accessibility (look for “One Breath”)** → Installed apps → One Breath → turn it on. The accessibility settings list the app under your phone's system language, so on a phone set to Chinese it appears as 喘口氣
3. **Is the switch greyed out?** That's Android protecting you from sideloaded apps:
   go back to the app and tap "Open App info" → ⋮ in the top-right corner → "Allow restricted settings" → then go back and turn on accessibility
4. On the "Groups" tab, tap "+ New group", pick a template (Social, Video…) or a custom name, tick the apps you want reminders for in the "Apps" tab (e.g. Instagram, YouTube, Threads), then tap "Save"
5. Recommended: open **your phone's system Settings app** (not the Settings tab inside One Breath) → Battery → One Breath → set to "Unrestricted", so Samsung's battery saver doesn't shut it down

Upgrading from an older version: the apps you had ticked are automatically moved into a group called "My apps" (or 「我的 App」 if the interface was in Chinese at the moment of the upgrade). From then on it's an ordinary group name and doesn't change with the language.

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
  - `Sessions.java`: handles what happens after the user continues or backs out
- `ui/`: screens
  - `PauseActivity.java`, `PauseOrbView.java`: the deep-breathing screen and breathing animation
  - `MainActivity.java`: the main screen and bottom navigation bar; `HomePage.java`, `GroupsPage.java`, `SettingsPage.java` are its three tabs
  - `GroupEditActivity.java`, `AppListAdapter.java`: group editing and app picking
  - `SeekRow.java`: the slider row shared by Settings and the group editor (a SeekBar plus its live value, e.g. "5 seconds", "Every 10 minutes")
  - `UiKit.java`: small shared helpers: dp conversion, checking whether the accessibility service is on, the feature chips on group cards, segmented-control styling, and making a whole row toggle its switch

`app/src/main/res/`
- `values/strings.xml`: English interface text (the default, used for any non-Chinese language)
- `values-zh/strings.xml`: Traditional Chinese interface text

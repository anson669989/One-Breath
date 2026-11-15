# 喘口氣（One Breath）

English | [繁體中文](README.md)

Caught your thumb opening Instagram again before you'd even decided to? One Breath gives you a few seconds to breathe before a distracting app opens. You can still open it — but this time it's your choice, not a reflex.

It doesn't block anything, lecture you or lock you out. Wait a moment and tap **I’m sure — continue to Instagram** to go in, or **Never mind, I’ll skip it** to go back to your home screen.

## What it's like to use

### A breath before the app opens
When you open one of the apps you've picked, a slowly breathing orb appears first, with a small question such as “What did you open it for?” Underneath, it tells you how many times you've opened that app today.

After a short wait (5 seconds by default, adjustable from 0 to 30), the button to continue fades in. There's deliberately no countdown, so you're not just watching the clock. Once you continue, reopening the same app over the next few minutes (5 by default, 1 to 30) won't interrupt you again.

### A check-in when you've been there a while
If you stay in the same app, One Breath checks in after a set amount of use (10 minutes by default, anywhere from 1 to 60) with something like “Keep going?” You can tap **Enough for now** or **A little longer**. This reminder screen has its own wait time. Only the time the app is actually on screen counts; time with the screen off doesn't.

### Groups
Sort your apps into groups such as “Social”, “Video” and “Games”. Each group decides for itself whether to use **Breathe before opening**, **In-use reminder** and **Also remind on their websites**, and can have its own wait times. An app belongs to one group at a time.

### Websites count too
If you pick an app that has a website, like X or YouTube, opening x.com or youtube.com in a browser triggers the same reminder. For sites without an app, add them under **Custom websites** in a group, for example `news.example.com`. This works in Chrome, Samsung Internet, Firefox, Edge, Brave, Opera, DuckDuckGo and a few other browsers.

### Today, at a glance
The Home tab shows how many breaths you've taken today and how many times you chose not to open the app. No scores and no streaks — just a quiet look at your day.

Need a break from all of it? The **Enable reminders** switch at the bottom of the Settings tab pauses everything. Your groups and settings stay as they are.

### Three tabs
- **Home**: whether the accessibility permission is on (with step-by-step help if it isn't), today's counts and a summary of your groups.
- **Groups**: one card per group, showing its app icons and which reminders are on, with a switch for the whole group. Tap a card to change its name, reminders, timing and custom websites, and use the **Apps** tab to search for and tick the apps that belong in it.
- **Settings**: the global timing, language, the About & privacy notes and, at the very bottom, the **Enable reminders** master switch.

## Your data stays on your phone
- One Breath has no internet permission, no ads and no account.
- Your settings, groups and daily counts are stored only on this phone, and the app never sends them anywhere. If you've turned on Android's own device backup, the system may include these settings in that backup.
- It needs the accessibility permission for one thing: knowing which app is in the foreground. In browsers it reads only the URL in the address bar, to recognise the websites you picked. Page content and everything else on screen is never read.
- All of the source code is in this repository, so you can check for yourself.

## Installing
You need a phone running Android 8.0 or newer.

### Downloading the APK
Download the latest APK from the [Releases page](https://github.com/anson669989/One-Breath/releases/latest). You don't need a GitHub account.

If you'd rather build it yourself, see the [development guide](docs/DEVELOPMENT.en.md).

### Setting it up
1. Copy the APK to your phone and tap it to install. The first time, you'll be asked to allow installing unknown apps.
2. Open One Breath, and on the Home tab tap **① Turn on accessibility (look for “One Breath”)** → Installed apps → One Breath → turn it on. When you come back, Home should say **✓ Running**. (Accessibility settings show the app under your phone's language, so on a phone set to Chinese look for 喘口氣.)
3. Switch greyed out? On Android 13 and later, that's Android protecting you from apps installed outside an app store. Go back to One Breath, tap **Open App info** → ⋮ in the top-right corner → **Allow restricted settings**, then turn on accessibility again.
4. On the Groups tab, tap **+ New group**, pick a template or type your own name, tick the apps you want reminders for on the **Apps** tab, and tap **Save**.
5. On Samsung phones, one more step is worth it: open your phone's own Settings app (not the Settings tab inside One Breath) → Battery → One Breath → set it to **Unrestricted**, so battery saving doesn't shut it down and reminders keep working.

New versions install straight over the old one, and your settings are kept. If you're upgrading from a very early version, the apps you'd ticked are moved into a group called “My apps” automatically.

## Language
The interface is available in English and Traditional Chinese. In the Settings tab, under **語言 / Language**, choose **System**, **繁體中文** or **English**. The change applies right away.

With **System**, the app uses Traditional Chinese if your phone's first language is any form of Chinese, and English otherwise. The app's name on your home screen and its description in the phone's accessibility settings follow the phone's own language.

## Building it yourself or contributing
Build steps, signing setup and how the app works under the hood are in [docs/DEVELOPMENT.en.md](docs/DEVELOPMENT.en.md).

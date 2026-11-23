# REEL_SCROLL_COUNTER

An Android app that counts the reels you swipe through and how long you scroll. It shows a small pill at the top of the screen, like a "dynamic island", with **Sprout**, a little character who gets sad and exhausted as you go past your limit.

```
 23 · 4:12
```

## Installing the app

**→ [Download the APK and follow the step-by-step install guide](INSTALLATION_GUIDE/README.md)**

Everything works offline. The app only goes online if you start or join a challenge with friends.

## Features

- **Counts reels** in Instagram Reels, YouTube Shorts, TikTok, Facebook Reels and Snapchat Spotlight
- **Timer** runs the whole time a reel app is open, whether you're swiping or not
- **Pill at the top of the screen**: left, center or right, with adjustable distance from the top. Tap it to see your limits.
- **Sprout**, an animated character, appears when the timer starts and gradually changes: active → worried → sad → drained → exhausted
- **Daily limits** for reels and minutes. You get **one warning for whichever limit you hit first**, then again at 2×, 3×…
- **Full-screen warning** with Sprout, your stats and **3 "what to do now" steps** (breathe, a healthy break, close the app). **Close app** sends you home; **Keep watching** unlocks after 5 seconds.
- **Sound + vibration** on warning (pick any phone sound). The reel's audio pauses while the warning shows.
- **Daily analysis**: today vs yesterday, 7- and 30-day charts, time of day, and a per-app split. Keeps 90 days of history.
- **Welcome screen** on first launch explaining that the app is fully offline
- **Light / Dark / System** theme
- **Challenge friends**: share an invite link and compete to watch the fewest reels by 11:59 PM. Live leaderboard, Reset locked during the challenge, end early only if everyone agrees, results notification at 11:59 PM
- **Private**: it only runs inside the apps you tick, and Android never connects it to UPI, banking or any other app. It works offline; only Challenge mode goes online, sharing just a nickname, reel count and time with the players in that challenge (anonymous sign-in)

## Build

Requires JDK 17 and the Android SDK (platform 34). Easiest: open the folder in **Android Studio** and press Run.

From the command line:

```bash
./gradlew assembleDebug            # → app/build/outputs/apk/debug/app-debug.apk
```

### Signed release build

Create `keystore.properties` in the project root (it's git-ignored; never commit it or the `.jks` file):

```properties
storeFile=reelcounter-release.jks
storePassword=...
keyAlias=reelcounter
keyPassword=...
```

```bash
./gradlew assembleRelease          # → app/build/outputs/apk/release/app-release.apk
```

Without `keystore.properties`, the release build is left unsigned.

### Challenge mode server

Challenge mode uses a free Firebase project. Setup steps and database rules are in [`firebase/`](firebase/README.md). Put the API key and database URL in a git-ignored `firebase.properties`; without it, the app builds fully offline and Challenge mode is turned off.

## Install & set up

Full illustrated steps: **[INSTALLATION_GUIDE](INSTALLATION_GUIDE/README.md)**. Short version:

1. Install the APK. If **Play Protect** blocks it ("sensitive permissions"): Play Store → profile → Play Protect → Settings → turn off scanning, install, then turn it back on. On Samsung you may also need to turn off **Auto Blocker** for a moment.
2. Open **Reel Counter** → **Turn on Reel Counter** → Accessibility → **Installed apps** → **Reel Counter** → On.
3. **Android 13+:** if the switch is greyed out, go to Settings → Apps → Reel Counter → ⋮ → **Allow restricted settings**.
4. **Samsung:** set Reel Counter's battery to **Unrestricted** so it isn't put to sleep.

Tip: turn on the **Reel Counter shortcut** in its Accessibility page to toggle it with the volume keys (handy before using UPI apps that object to any accessibility app).

## How detection works

An Accessibility Service, limited to the reel apps' package names, watches **scroll events** from each app's full-screen reel pager. It recognises the pager by view ID (`clips_viewer…` for Instagram, `reel_recycler` for YouTube, and so on). When the pager reports a new item index, that's +1 reel. If an app update changes those IDs, turn on **"Count every swipe"** in settings, or add the new ID to `REEL_IDS` in `ReelCounterService.kt`.

## Code map

| File | What it does |
|---|---|
| `ReelCounterService.kt` | Detects reels, runs the timer, checks limits, shows the pill and warning, handles sound and audio focus |
| `PillView.kt` | The top-of-screen pill (with Sprout) |
| `CharacterView.kt` | Sprout: Canvas drawing and mood animation |
| `WarningView.kt` | Full-screen warning with "what to do now" steps |
| `MainActivity.kt` | Home screen: hero card plus settings |
| `WelcomeActivity.kt` | First-launch welcome and privacy screen |
| `ChallengeActivity.kt` | Challenge screen: rules pop-up, invite, leaderboard, end vote, results |
| `Challenge.kt`, `Cloud.kt` | Challenge logic and a minimal Firebase client (anonymous sign-in + Realtime Database over HTTPS) |
| `Notify.kt` | Results notification at 11:59 PM |
| `docs/join.html` | Invite page that opens the app (GitHub Pages) |
| `firebase/` | Database security rules and server setup guide |
| `AnalysisActivity.kt` + `BarChartView.kt` | Daily analysis screen and charts |
| `History.kt` | Today's per-app and per-hour stats, plus 90-day history |
| `Prefs.kt`, `Mood.kt` | Settings, limits and mood thresholds |
| `sprout-preview.html` | Browser preview of Sprout's moods (same drawing math) |

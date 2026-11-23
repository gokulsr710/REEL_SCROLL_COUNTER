#  How to install Reel Counter on your phone

**File:** [`ReelCounter-v2.7.apk`](ReelCounter-v2.7.apk) · **Needs:** Android 8 or newer · **Size:** about 650 KB

> Reel Counter is for **Android only**. iPhones don't allow apps like this.

---

## Read this first: why your phone will warn you

Reel Counter needs **Accessibility permission**. That's the only way Android lets an app notice when you swipe to a new reel inside Instagram, YouTube and the others.

Because Accessibility is a powerful permission, and this app isn't from the Play Store, **Google Play Protect will warn you or block the install**. You may see *"This app can request sensitive permissions"* or *"Blocked by Play Protect"*. Samsung phones may also block it with **Auto Blocker**.

This happens to **every** app installed outside the Play Store that uses Accessibility. It doesn't mean the app is harmful. To install it, you'll **turn off the scan for a minute, install, and turn it straight back on.**

##  Your privacy: this is an offline app

- **No internet permission.** The app doesn't ask for internet access at all, so Android itself prevents it from sending anything anywhere. Its only permission is `VIBRATE`. You can check this in [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml).
- **Nothing is sent** to the developer or anyone else: no data, no phone details, no contacts, no messages, no analytics, no ads.
- **It only works inside the apps you tick:** Instagram, YouTube, TikTok, Facebook and Snapchat. Android never connects it to **UPI, banking, messages, gallery or any other app**.
- **What it looks at:** only the *scroll* events of the reels screen, to count swipes. It doesn't read your messages, comments or what's in the videos.
- **Everything stays on your phone:** your count, time and history. Uninstalling the app deletes all of it.
- **The full source code is public** in this repository, so anyone can check it.

---

## Step-by-step install

### Step 1: Download the APK
On your phone, open this page → tap **`ReelCounter-v2.7.apk`** → tap **Download** (the download icon, or *View raw*).
It saves to your **Downloads** folder.

> On a computer instead? Download it, then copy it to the phone with a USB cable or Google Drive. **Don't send it through WhatsApp**, which can damage or rename the file.

### Step 2: Turn off Google Play Protect for a moment
1. Open the **Play Store**.
2. Tap your **profile picture** (top right) → **Play Protect**.
3. Tap **Settings** (gear icon, top right).
4. Turn **off** *Scan apps with Play Protect* → tap **Turn off**.

### Step 3 (Samsung only): Turn off Auto Blocker for a moment
**Settings → Security and privacy → Auto Blocker → Off**

### Step 4: Install
1. Open the **My Files** / **Files** app → **Downloads** → tap **`ReelCounter-v2.7.apk`**.
2. If it says *"Your phone is not allowed to install unknown apps from this source"*: tap **Settings** → turn on **Allow from this source** → go back.
3. Tap **Install** → **Open**.

### Step 5: Turn the protection back on 
- Play Store → profile → **Play Protect** → **Settings** → turn **Scan apps with Play Protect** back **on**.
- Samsung: **Auto Blocker → On**.

Reel Counter stays installed.

### Step 6: Turn on Reel Counter (one time only)
1. Open **Reel Counter**. The first time, a welcome screen explains that the app is fully offline. Tap **OK, let's go**, then tap **Turn on Reel Counter**.
2. In Accessibility, go to **Installed apps** (or *Downloaded apps*) → **Reel Counter** → turn it **On** → **Allow**.

**Switch greyed out, or a "Restricted setting" message?** (Android 13 and newer)
1. Go to **Settings → Apps → Reel Counter**.
2. Tap **⋮** (top right) → **Allow restricted settings** → enter your PIN.
3. Go back and repeat Step 6.

### Step 7: Stop the phone from putting it to sleep
So the pill keeps showing up:
- **Settings → Apps → Reel Counter → Battery → Unrestricted**
- **Samsung:** also **Settings → Battery → Background usage limits → Never sleeping apps → + → Reel Counter**
- **Xiaomi / Redmi / POCO:** App info → **Autostart: On**, Battery saver → **No restrictions**

### Step 8: Try it
1. In Reel Counter, set **Reels per day** to **3** → **Save limits**.
2. Open **Instagram Reels** or **YouTube Shorts**. The pill with Sprout appears at the top.
3. Swipe 3 reels. You'll see the full-screen warning.
4. Set your real limits (for example 100 reels / 30 minutes).

---

## Troubleshooting

| Problem | Fix |
|---|---|
| **"App not installed"** | Check you have Android 8+. Download the file again (it may be damaged). Uninstall any older Reel Counter first. Check you have free storage. |
| **"Blocked by Play Protect"**, no *Install anyway* | Do **Step 2** (turn off scanning), then install. |
| **Samsung blocks it silently** | Do **Step 3** (Auto Blocker off). |
| **Accessibility switch is greyed out** | **Allow restricted settings** (see Step 6). |
| **Pill doesn't show** | Check Reel Counter is **On** in Accessibility and the app is ticked in *Track these apps*. Do **Step 7**. |
| **Pill shows but count stays at 0** | Turn on **"Count every swipe"** in Reel Counter settings. |
| **Pill covers the clock** | Settings → Counter pill → move **Distance from top**, or pick **Right**. |
| **A UPI or bank app complains about accessibility** | Some banking apps object to *any* accessibility app. Turn Reel Counter off while paying. Quick way: Accessibility → Reel Counter → **Reel Counter shortcut** → hold both **volume keys** to toggle it. |
| **Stopped working after a while** | The phone put it to sleep. Do **Step 7**, then turn it on again in Accessibility. |

## Uninstall
**Settings → Apps → Reel Counter → Uninstall.** All its data is deleted with it.

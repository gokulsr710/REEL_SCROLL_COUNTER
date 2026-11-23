# Challenge mode: server setup (Firebase)

Challenge mode needs a small online database so friends' phones can see each other's scores. It uses a free **Firebase** project. The app talks to Firebase over plain HTTPS (no Firebase SDK), using **anonymous sign-in**: players never enter an email or phone number.

Only this is stored, and only for players in a challenge: **nickname, reel count, scrolling time, "wants to end" vote, and timestamps.**

## 1. Create the Firebase project
1. Go to <https://console.firebase.google.com> and sign in with your Google account.
2. **Create a project** → name it `reel-counter` → turn **Google Analytics off** → **Create project**.

## 2. Turn on anonymous sign-in
1. Left menu → **Build → Authentication** → **Get started**.
2. **Sign-in method** tab → **Anonymous** → **Enable** → **Save**.

## 3. Create the Realtime Database
1. Left menu → **Build → Realtime Database** → **Create database**.
2. Pick a location close to your players (for example **Singapore (asia-southeast1)** for India) → **Next**.
3. Choose **Start in locked mode** → **Enable**.
4. Open the **Rules** tab, replace everything with the contents of [`database.rules.json`](database.rules.json), then click **Publish**.
5. Copy the database URL shown at the top of the **Data** tab, for example `https://reel-counter-default-rtdb.asia-southeast1.firebasedatabase.app`.

## 4. Get the API key
1. Click the **gear icon → Project settings**.
2. **General** tab → **Your apps** → click the **Android** icon.
3. Package name: `com.reelcounter.app` → **Register app** → **Download google-services.json**. You can skip the remaining steps of that wizard.

## 5. Connect the app
Create a file named `firebase.properties` in the project root (it's git-ignored):

```properties
apiKey=AIza...            # "current_key" from google-services.json, or Project settings → Web API Key
databaseUrl=https://reel-counter-default-rtdb.asia-southeast1.firebasedatabase.app
```

Then build: `./gradlew assembleRelease`. Without `firebase.properties`, the app still builds and works fully offline; the Challenge screen just says it isn't available.

> The Firebase API key isn't a password: it only identifies the project, and the **database rules** decide what anyone can read or write. It's still kept out of the repository so others don't use your free quota.

## 6. Turn on the invite page (GitHub Pages)
Invite links point to `https://gokulsr710.github.io/REEL_SCROLL_COUNTER/join.html?c=CODE`, which opens the app.

1. On GitHub, open the repository → **Settings → Pages**.
2. **Source:** *Deploy from a branch* → Branch: **main**, folder: **/docs** → **Save**.
3. After a minute, the page is live. If the button doesn't open the app, players can type the 6-character code in **Challenge friends**.

## How the rules protect the data
- Players must be signed in (anonymously) to read a challenge, and they need its 6-character code.
- Nobody can list all challenges.
- Each player can write **only their own** score, and only until 2 minutes after 11:59 PM on the challenge day.
- Values are type- and range-checked, and no extra fields are allowed. (The 10-player limit is enforced by the app.)
- Once a challenge has ended, its status can't be changed back.

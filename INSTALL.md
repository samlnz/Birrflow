# Installing BirrFlow on your Android phone (no Android Studio needed)

GitHub builds the app for you every time the code changes. You only download and install it.

## 1. Download the APK

1. On your phone, open the **Releases** page of the public build repository
   (`https://github.com/samlnz/BirrFlow/releases/latest`).
2. Under **Assets**, tap **BirrFlow.apk**. Your browser downloads it (about 30–60 MB).

Tip: `https://github.com/samlnz/BirrFlow/releases/latest/download/BirrFlow.apk` always points to
the newest build — you can bookmark it.

### If GitHub Actions can't run (billing locked)

GitHub won't start any build while the account's billing is locked, even on a public repository.
Either fix it (**github.com → Settings → Billing and plans → Payment information**, pay or update the
card), or build for free on **Codemagic** instead:

1. Go to **https://codemagic.io/signup** and choose **Sign up with GitHub** (allow access to the
   `Birrflow` repository).
2. **Add application** → pick **samlnz/Birrflow** → project type **Android App**.
3. Codemagic finds `codemagic.yaml`; choose the workflow **BirrFlow debug APK** and tap
   **Start new build**.
4. After about 10 minutes the build page shows **BirrFlow.apk** under *Artifacts* — download it on
   your phone (Codemagic also emails a link).

## 2. Allow installing it

Android blocks apps that don't come from the Play Store until you allow it once:

1. Open the downloaded file (from the notification, or the **Files / Downloads** app).
2. If Android says *"For your security, your phone is not allowed to install unknown apps from this
   source"*, tap **Settings** and switch on **Allow from this source**, then go back.
3. Tap **Install**. If **Play Protect** warns that the app is unknown, tap **More details →
   Install anyway** (it warns because the app isn't from the Play Store).

## 3. First start

1. Open **BirrFlow**.
2. Allow **SMS** (so it can read bank SMS) and **Notifications** (for the categorize buttons and the
   weekly briefing).
3. If the dashboard shows a battery-optimization card, follow it so background SMS detection keeps
   running.
4. Optional: AI Advisor → settings → paste your **Gemini API key** (free from
   https://aistudio.google.com/apikey) to turn on the AI advisor.

## Updating later

Download the new **BirrFlow.apk** the same way and install it over the old one — your data stays.

## If installation fails

- **"App not installed" / "package conflicts with an existing package"**: an older BirrFlow built
  elsewhere (for example from Google AI Studio) is signed with a different key. Uninstall the old
  app first, then install this one. Uninstalling deletes that app's local data — link cloud sync in
  the old app first if you want to keep it.
- **"There was a problem parsing the package"**: the download didn't finish; download it again.
- **The newest build has no APK**: open the repository's **Actions** tab; a red ❌ means that build
  failed and the previous release is still the latest working one.

## How the build works (for reference)

`.github/workflows/build-apk.yml` runs on every push to `main`: it builds the debug APK with
Gradle 9.3.1 and Java 21, runs the unit tests, and publishes the APK as a GitHub Release.
GitHub Actions is free for **public** repositories.

Every build must be signed with the same key so it installs as an update. The key is **not** in the
repository: the first build creates it and keeps it in the GitHub Actions cache. GitHub drops caches
that are unused for 7 days, so if you don't build for more than a week, the next build gets a new key
and you'd have to uninstall once. To make the key permanent (optional):

1. On a computer with Java, run
   `keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"`
   then `base64 -w0 debug.keystore` and copy the output.
2. In the repository: **Settings → Secrets and variables → Actions → New repository secret**, name it
   `DEBUG_KEYSTORE_BASE64`, paste the text, save. (Uninstall once after the first build that uses it.)

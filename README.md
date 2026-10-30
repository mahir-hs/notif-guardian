# Notif Guardian

Real-time notifications on Xiaomi HyperOS, without opening the app first.

**Website and download: https://mahir-hs.github.io/notif-guardian/** · [Changelog](CHANGELOG.md)

On Xiaomi phones, WhatsApp, Telegram, Gmail and other messages often arrive only when you unlock
the phone or open the app. Notif Guardian finds out why, fixes what it can, and tells you when
something breaks again.

Tested on a Redmi Note 14 Pro+ with HyperOS 3.0 (Android 15). Other HyperOS and MIUI versions may
need adjustments; diagnostics reports are welcome.

## Why notifications are late on HyperOS

Almost every app gets its push notifications through one connection held open by
**Google Play services** (Firebase Cloud Messaging). HyperOS has its own process freezer, called
Millet, that freezes background apps when the screen turns off. It ignores Android's normal battery
whitelists and only leaves alone the apps in a hidden list (`MILLET_NO_RESTRICT_APP`).

Setting an app's **Battery saver** to **No restrictions** puts it on that list. But:

- Google Play services is a system app, so HyperOS offers **no battery saver setting for it**.
  It gets frozen whenever the screen is off, and push stops for every app until you unlock.
- HyperOS **rebuilds the list** whenever any app's battery saver changes, dropping anything it
  didn't put there itself.

You can see it yourself over adb: `adb shell dumpsys greezer` shows Google Play services
(`uid 10xxx`) frozen right after `SCREEN OFF`.

## What the app does

**Notif Guardian**

- Lets you choose which apps to protect, and checks five things for each one: running, autostart,
  battery saver, background activity, and whether HyperOS freezes it with the screen off.
- Opens the exact HyperOS settings screen to fix each problem.
- Alerts you when the system force-stops a protected app, with a tap to revive it.
- Measures how late each notification arrived (time sent vs. time shown) and lists the late ones.
- Keeps Google Play services on HyperOS's exemption list with Guardian Helper (below), putting it
  back within milliseconds whenever HyperOS removes it.
- Exports a diagnostics zip (logs and settings state, never message content) to share for bug reports.
- Updates itself from this repo's GitHub releases, with download and install progress. Android only
  accepts updates signed with the same key, so only genuine releases install.

**Guardian Helper** is a tiny companion with no screen. Android only lets apps built for
Android 5.1 or older write the hidden list, so the helper targets that version and does exactly one
thing: add packages to the list when Guardian asks. Only apps signed with the same key as Guardian
can ask it.

## Privacy

Guardian goes online only when you tap **Check for updates** (to read this repo's latest release
and download it) or **Show ways to donate** (to load the donation options from this project's
website), and only to GitHub. Nothing else leaves your phone unless you share a
diagnostics file yourself. Guardian reads notification timing (who posted, when), never the text.
Guardian Helper has no internet permission at all.

## Install

Download `NotifGuardian-x.y.z.apk` and `GuardianHelper-x.y.z.apk` from the
[Releases](../../releases) page.

### 1. Notif Guardian

Install the APK normally and open it. In **Guardian is set up**:

1. **Notification access**: lets it measure delays. If Android blocks it because the app "comes
   from an unknown source", open Guardian's **App info → ⋮ → Allow restricted settings**, then try
   again. Android asks this once for any app installed from a downloaded file (Android 13+
   "restricted settings"); installs with `adb install` aren't affected.
2. **Guardian's alerts**: allow notifications.
3. **No battery restrictions**, **Autostart**, and **Not frozen when the screen is off**: each row
   opens the right HyperOS screen. Set Battery saver to **No restrictions**.

Then tap **Add apps** and choose the apps whose notifications matter to you.

### 2. Guardian Helper (needed for Google Play services)

Android 14+ blocks installing apps built for old Android versions, so the helper has to be
installed once from a computer with [adb](https://developer.android.com/tools/releases/platform-tools):

1. On the phone: **Settings → About phone**, tap **OS version** 7 times. In **Developer options**
   turn on **USB debugging** and **Install via USB** (Xiaomi requires a Mi account for this).
2. Connect the phone and run:
   ```
   adb install --bypass-low-target-sdk-block GuardianHelper-x.y.z.apk
   ```
   Watch the phone and tap **Install** on HyperOS's confirmation screen.
3. In Notif Guardian, open **Keep Google Play services awake** and turn every row green:
   - **Helper can change system settings**: turn on **Modify system settings**.
   - **Helper autostart**: turn it on.
   - **Helper never frozen**: set its battery saver to **No restrictions**.
   - **Helper is responding**: tap **Open helper**, confirm Android's one-time permission review,
     then **Test helper**.

HyperOS turns the helper's autostart off again whenever the helper is updated; the section will
show it in red if that happens.

## Good to know

- **Lock your messaging apps in Recents** (long-press the card, tap the lock) so "Clear all"
  doesn't kill them.
- **Telegram** may delay pushes on purpose while you're active on Telegram Desktop or Web. That
  happens on Telegram's servers; the phone delivers the push the moment it arrives.
- `*#*#426#*#*` in the dialer opens Google's FCM diagnostics: it shows whether the push connection
  is up.

## Building

Requirements: JDK 17+ and the Android SDK (platform 36).

```
./gradlew :app:assembleDebug :helper:assembleDebug
```

Release builds are signed with the key described in `keystore.properties` (not in the repo):

```
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`scripts/build-release.ps1` builds both signed APKs into `dist/` with SHA-256 checksums. Without
`keystore.properties`, release builds fall back to the debug key. **Guardian and Guardian Helper
must be signed with the same key**, or Guardian can't call the helper.

`scripts/pull-diagnostics.ps1` collects logs and HyperOS state from a connected phone over adb.

The website lives in `docs/` and is served by GitHub Pages (Settings → Pages → Deploy from a
branch → `main`, folder `/docs`). Its download buttons always point at the latest release.

## Releasing

The in-app updater reads the latest release of the repo set in `gradle.properties`
(`updateRepo=owner/name`). For each release:

1. In `app/build.gradle.kts`, raise `versionCode` by one and set `versionName` (e.g. `0.2.0`).
   Add the version's section to `CHANGELOG.md`; it doubles as the release notes.
2. Run `scripts/build-release.ps1`.
3. Create a GitHub release with the tag `v` + versionName (e.g. `v0.2.0`) and attach **all three**
   files from `dist/`: `NotifGuardian-<version>.apk`, `GuardianHelper-<version>.apk` and
   `SHA256SUMS.txt`. The updater looks for an asset named `NotifGuardian-*.apk` and checks it
   against `SHA256SUMS.txt`. The release notes are shown in the app.

The updater only updates Guardian. Guardian Helper rarely changes; when it does, reinstall it with
adb as above (and turn its autostart back on).

## Support development

Notif Guardian is free. If it fixed your notifications, you can donate:
see [the website](https://mahir-hs.github.io/notif-guardian/#support). The options are listed in
`docs/support.json`, which both the website and the app read, so they can change without an app
update.

## License

[MIT](LICENSE)

# Changelog

All notable changes to Notif Guardian. Each version's section is also its GitHub release notes,
which the in-app updater shows before you install.

## 0.1.4 (2026-10-30)

- Install Guardian Helper from inside Guardian: the **Install** button on its setup row downloads
  it, with progress, and opens the install screen.
- **Check for updates** now also offers a newer Guardian Helper when there is one.
- Release files now name Guardian Helper with its own version, so the app only offers it when it
  really changed.

Guardian Helper is unchanged. If you already have it, you don't need to reinstall it.

## 0.1.3 (2026-10-30)

- No computer needed: Guardian Helper installs on the phone like any download (tested on
  HyperOS 3). The setup row now has a **Download** button instead of adb instructions; adb is
  only a fallback if your phone refuses the helper.

Guardian Helper is unchanged. If you already have it, you don't need to reinstall it.

## 0.1.2 (2026-10-30)

- New **Support development** section: if Notif Guardian fixed your notifications, you can now
  donate. In Bangladesh by bKash or Nagad, with a button to copy the number; international options
  are coming. Tap **Show ways to donate** to see them. The options load from the project website
  only when you tap it.

Guardian Helper is unchanged. If you already have it, you don't need to reinstall it.

## 0.1.1 (2026-10-30)

- Much smaller download: 21 MB down to 2.2 MB.
- When Android blocks Notification access because the app was installed from a downloaded file,
  Guardian now explains how to allow it (App info → ⋮ → Allow restricted settings) and opens
  App info for you.
- Update checks are more reliable: the checksum file is read correctly in more cases.

Guardian Helper is unchanged. If you already have it, you don't need to reinstall it.

## 0.1.0 (2026-10-30)

First release.

- Keeps Google Play services from being frozen by HyperOS while the screen is off, so
  notifications for every app arrive in real time. Uses Guardian Helper to put it back on
  HyperOS's exemption list within milliseconds whenever HyperOS removes it.
- Protect any app you choose. Each app shows its delivery path at a glance (running, autostart,
  battery saver, background activity, frozen or not) with a one-tap fix for anything broken.
- Alerts you when HyperOS kills a protected app, with a tap to bring it back.
- Measures how late each notification arrived and lists the late ones.
- Diagnostics export for bug reports (logs and settings, never message content).
- Updates itself from GitHub releases, with download and install progress.

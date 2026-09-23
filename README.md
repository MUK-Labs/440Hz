# 440

A small offline Android app for practising recall of A440.

**Android only:** Requires Android 8.0 or newer. There is no iPhone version or
Play Store listing yet.

Your phone gives a quiet double vibration at random intervals. Imagine or sing
A; when ready, open the notification and press **440** to hear the reference.
Or, with **Shake after reminders** enabled, shake the phone twice within 60 seconds
of the vibration to hear A without opening the app. Opening the app never plays
audio automatically. The button works with reminders off, too. A home-screen
widget also plays the tone directly. No microphone, accounts, analytics or
network access.

## Install on a new Android phone

**Getting the app:** This repository is currently private and has no published
APK release. Ask the maintainer for an APK file; a link to this repository or
its GitHub Actions builds will not work for people without access. A GitHub
“Source code” ZIP is not an Android app.

1. Download the **.apk** file from the maintainer onto your Android phone.
   If you transferred it from a computer, find it in **Files → Downloads**.
2. Tap the APK and choose **Install**. If Android blocks the installation,
   follow its prompt to **Settings → Allow from this source** for the browser
   or file manager you used, then go back and tap **Install** again. The exact
   wording varies by phone. You can turn that permission off after installing.
3. Tap **Open**, or find the app named **440** among your apps. It works offline
   and needs no account.

Only install an APK you received from a source you trust. If you get an
installation error while updating an older test build, see
[Troubleshooting](#troubleshooting) before uninstalling it.

## Set it up and try it

1. Turn up **media volume** and tap the large **440** button. You should hear
   a three-second A. Check whether sound goes to headphones or a Bluetooth
   device if the phone seems silent.
2. Switch on **Random reminders** and allow notifications when Android asks.
   Open **Reminder settings** to choose your active days and hours, reminder
   frequency, and how long the reminders should run; then tap **Save**.
3. Tap **Test reminder**. You should get a quiet notification with a double
   vibration. Imagine or sing A, tap the notification to open the app, then
   press **440** to compare. Tapping the notification alone does not play sound.
4. Optionally enable **Shake after reminders**. Tap **Test reminder** again,
   wait for the vibration to finish, and shake the phone twice briskly within
   60 seconds to hear A without opening the app. This can work with the screen
   locked. A quiet ongoing notification indicates shake mode is available.
   The option is unavailable on phones without an accelerometer.
5. Optionally tap **Add home-screen widget** for a one-tap reference tone.

## Troubleshooting

- **No sound:** Raise *media* volume, check the current speaker or headphones,
  and use the large **440** button to test playback.
- **No reminder or vibration:** Check that **Random reminders** is on, your
  chosen hours and days include the current time, and the run has not ended.
  In **Notification settings**, allow notifications for 440 and vibration for
  **Pitch reminders**. Do Not Disturb can silence them. **Test reminder** lets
  you check without waiting for a random delivery. Android battery saving can
  delay or reduce scheduled reminders.
- **Shake does not play A:** Enable both **Random reminders** and **Shake after
  reminders**. Keep the phone in your hand; wait for the vibration to end and
  shake twice within 60 seconds. Try **Reminder settings → Shake sensitivity**
  if needed. Open the app again after a reboot, update, or force-stop to resume
  locked-screen shake. Do Not Disturb disables shake playback.
- **An update will not install:** Android requires an update to use the same
  signing key as the installed app. Ask the maintainer for a compatible APK.
  Uninstalling an old test build can make a differently signed build install,
  but it erases this app's settings.

## Home-screen widget

Open **440** and tap **Add home-screen widget**.
Confirm placement in your launcher's prompt. Alternatively, long-press an empty
part of your home screen, choose **Widgets**, and drag **440** onto it.

Tap the green **440** widget to hear the same three-second reference without
opening the app. It works with reminders off and can be resized on launchers
that support it. Media volume and the current speaker/headphones apply.

Only a tap starts playback. Adding/resizing the widget, rebooting, and receiving
a reminder never play sound. Widget playback uses a short foreground media
service and a silent playback notification; both stop after the tone finishes.
Repeated taps restart the tone without overlap, including across multiple widgets
and the app button. The widget has no periodic updates or continuously running service.

## Reminder-only shake (0.3.0)

Enable **Random reminders** and **Shake after reminders** in the app.
After each delivered reminder, recall A and give the phone two brisk shakes
within 60 seconds. The tone plays once; sensing then stops until the next reminder.
Keep the phone in your hand. **Reminder settings → Shake sensitivity** offers
Gentle, Normal, and Firm. Normal is the default; shake mode defaults to off.

Use **Test reminder**, lock your phone, wait for the vibration to finish, then
shake twice. The first 1.5 seconds are ignored so the notification vibration
does not trigger playback. There is no shake playback before a reminder,
after timeout, or after already playing the reference with the app or widget.
Starting the app, restarting a service, or rebooting never arms a window.
Do Not Disturb prevents arming and gesture playback.

Android requires a quiet ongoing **Shake after reminders** notification while
this option and reminders are enabled. Its **Turn off shake** action stops the
service, sensor listener, and any gesture playback. The accelerometer is only
registered during the reminder window, with a bounded CPU wake lock so it can
work with the screen off; no sensor data is stored or uploaded. Outside that
window there is no sensor listener or held wake lock. Gesture playback uses the
same audio-focus-aware player as the app and widget.

The session is started from the visible app, not an inexact background alarm.
After reboot, app update, force-stop, or manually stopping the service in Android,
open 440 again to enable locked-screen shake for future reminders. Android may
restart a killed service, but it restores no pending gesture or sound.
Manufacturer battery policies can still affect locked-screen delivery; validate
on the actual phone. The notification channel must be enabled for reminders.
If the device has no accelerometer, the shake switch is unavailable.

Implementation: a declared special-use foreground service maintains the optional
gesture session, adding the mediaPlayback type only during the reference tone.
The detector uses acceleration magnitude, two distinct peaks separated by a low
reading, a 180–1000 ms gap, and monotonic deadlines. Automated Java checks cover
window boundaries, grace period, cancellation, one-shot consumption, sensitivity,
invalid/stale ordering, and isolated or sustained motion. Real walking, pocket
movement, and deliberate-shake sensitivity still need device testing.

## Signed builds for sharing

There is currently no public APK download. Before sharing the README with
potential users, provide a signed APK through a link they can actually access.
This repository is private, so a Release here would also require repository
access. Add the download link to the installation section above when available.

With Android Studio, generate the Gradle wrapper first as below, open this
project, then choose **Build → Generate Signed App Bundle or APK → APK**.
Create a keystore outside the repository, keep a secure backup, and use the
same key for every update. Increase `versionCode` and `versionName` in
`app/build.gradle.kts` for updates. The GitHub Actions workflow produces a
debug APK for testing; its artifact requires repository access, expires, and
may use a different signing key on later builds. Play Store publishing is
optional and is not configured in this project.

## Local development

Use JDK 17, Android SDK 35 and Gradle 8.11.1. This archive does not contain a
Gradle wrapper binary. With Gradle 8.11.1 available:

```bash
gradle wrapper --gradle-version 8.11.1 --distribution-type bin
./gradlew :app:assembleDebug :app:lintDebug
bash tools/check.sh
```

Commit the generated wrapper files for subsequent builds. GitHub Actions installs
the pinned Gradle version itself, so it does not require a wrapper.
The UI is Kotlin with platform Android views; the independent scheduler is Java.

## Settings and behavior

- Enable / disable reminders. Disabling cancels the scheduled alarm and notification.
- 1–6 reminders per active hour or active day, approximately.
- Start / end times, selected weekdays, and an optional 1-hour, 8-hour, 1-day
  or 7-day run. Saving settings restarts the chosen duration if enabled.
- Equal start/end means 24 hours. An overnight window belongs to its start day:
  Friday 22:00–02:00 includes early Saturday.
- Each interval is drawn uniformly from 0.5–1.5 times the nominal mean, with a
  floor of five eligible minutes. Waiting pauses outside active windows. A short
  window or the minimum gap can lower the effective frequency.
- Uses `AlarmManager.setAndAllowWhileIdle`, deliberately without exact-alarm access.
  Android may delay and reduce reminders, especially in Doze. Late reminders
  outside active hours or after expiry are discarded. No catch-up bursts.
- A single notification is replaced by the next reminder, preventing a pile-up.
  It expires after one hour; swipe to dismiss. Tapping opens the app, not audio.
- The notification channel is configured without sound and with vibration.
  Android permits the user to override its sound and vibration settings.
  Do Not Disturb and manufacturer battery policies remain in control.
- Reboot, app update and time-zone/time changes rebuild the schedule.
  After force-stopping the app, open it again to resume reminders.
- Audio uses media volume and the current audio route. Playback requests audio
  focus and does not overlap on repeat taps. App-button playback stops when leaving
  the app; widget playback finishes on the home screen.
- The bundled three-second WAV is an original 440 Hz sine with a gentle attack
  and decaying envelope. Regenerate with `python3 tools/generate_tone.py`.

## Device acceptance checks

- Enable reminder-only shake; use Test reminder, lock, and shake twice within 60 seconds.
- Verify one playback, timeout silence, and no arming from service restart or boot.
- Verify manual app/widget playback consumes a pending shake window.
- Test walking/pocket movement, all sensitivity levels, DND, calls, and headphones.
- Disable shake/reminders, use the notification off action, and test expiry during a window.
- Revoke notifications and verify no new gesture playback.
- Add the widget through the app and the launcher's widget picker; resize it.
- Tap after the app process has been closed; confirm playback stays on the home screen.
- Tap rapidly and use two widgets; confirm only one tone plays and the service stops.
- Verify widget playback with reminders off and notification permission denied.
- Add/resize/remove the widget and reboot: no automatic sound.
- Check the widget on Android 8.0 and Android 15+; check large fonts.
- Button plays A440; launch and notification taps are silent; repeated taps do not overlap.
- Check media volume, headphones, calls/audio focus, and leaving the app mid-tone.
- Grant, deny and revoke notification permission; verify the screen explains blocked reminders.
- Test vibration; verify notification has no sound; check DND and channel vibration overrides.
- Enable reminders, lock phone, and observe several deliveries in and outside active hours.
- Disable after scheduling; verify no further reminder. Test overnight and selected-day windows.
- Test expiry, reboot, time-zone changes, and force-stop/reopen.
- Check large font sizes, small screens and Android 15 edge-to-edge insets.

## References

- [Android alarm scheduling](https://developer.android.com/develop/background-work/services/alarms)
- [Notification channels](https://developer.android.com/develop/ui/views/notifications/channels)

Code and generated audio: MIT license. This is a pitch-recall practice tool;
it does not measure singing or claim to establish perfect pitch.

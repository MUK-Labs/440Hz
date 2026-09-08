# 440

A small offline Android app for practising recall of A440.

Your phone gives a quiet double vibration at random intervals. Imagine or sing
A; when ready, open the notification and press **440** to hear the reference.
Opening the app never plays audio automatically. The button works with reminders
off, too. No microphone, accounts, analytics, network access or background audio service.

## Current status

First source implementation, not yet compiled or tested on an Android device.
The Java scheduling engine has passed local boundary and randomized tests.
The bundled audio has been checked for duration and frequency. The included
GitHub workflow builds an installable **debug APK** and runs Android lint;
a successful workflow and device checks below are still required.

## Get an APK using GitHub (no Android Studio needed)

1. Create an empty GitHub repository, for example `pitch440`.
2. Unzip this project on your Mac. In Terminal, enter its folder and run:

   ```bash
   git init -b main
   git add .
   git commit -m "Initial 440 Android app"
   ```

3. Add the repository URL shown by GitHub and push:

   ```bash
   git remote add origin https://github.com/YOUR_USERNAME/pitch440.git
   git push -u origin main
   ```

4. Open **Actions → Build Android APK**. After it succeeds, download the
   `pitch440-debug-apk` artifact and unzip it.
5. Transfer `app-debug.apk` to your Android phone and open it. Allow that source
   to install apps if prompted. Minimum supported version: Android 8.0.
6. Open **440**, press the large button, and adjust media volume. Turn on
   reminders and grant notification permission when requested.

The `.github` directory must be included when uploading; the Terminal method
above includes it. A downloaded source ZIP itself is not installable on Android.

The CI APK is for initial testing. Fresh CI machines can use different debug
signing keys, so a later debug APK may require uninstalling the previous build
(which erases its settings). For stable distribution, create and safely retain
one release signing key. Never commit that key or passwords.

## Signed builds for sharing

With Android Studio, generate the Gradle wrapper first as below, open this
project, then choose **Build → Generate Signed App Bundle or APK → APK**.
Create a keystore outside the repository, keep a secure backup, and use the
same key for every update. Attach the signed APK to a GitHub Release. Increase
`versionCode` and `versionName` in `app/build.gradle.kts` for updates.
Play Store publishing is optional and is not configured in this project.

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
  focus, stops when the app leaves the foreground, and does not overlap on repeat taps.
- The bundled three-second WAV is an original 440 Hz sine with a gentle attack
  and decaying envelope. Regenerate with `python3 tools/generate_tone.py`.

## Device acceptance checks (still to do)

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

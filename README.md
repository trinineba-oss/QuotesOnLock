# Quotes on Lock

An Android app that keeps a quotation on your lock screen.

Android doesn't let normal apps draw on the lock screen, so the app posts the quote as a silent, ongoing notification with public lock-screen visibility. It rotates on a schedule you choose, and the notification has a **Next quote** button.

## Features

- Built-in quotes (`app/src/main/res/raw/quotes.txt`, one `text|author` per line) plus your own, stored on the device.
- Rotation every 15 minutes up to daily (WorkManager).
- The quote comes back after a reboot.
- No internet permission, no accounts, no network access.

## Lock screen setup

1. Open the app and turn on **Show quote on lock screen**. Allow notifications when asked.
2. In system **Settings > Notifications > Notifications on lock screen**, choose to show the content, and keep silent notifications enabled.

On Android 14 and later you can swipe an ongoing notification away. It reappears at the next scheduled change, or when you open the app or turn the switch off and on.

## Building

Debug APK:

```sh
./gradlew assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

Needs JDK 17+ and the Android SDK (API 35). The GitHub Actions workflow builds the APK on every push and uploads it as an artifact.

# Quotes on Lock

An Android app that keeps a quotation on your lock screen.

Android doesn't let normal apps draw on the lock screen, so the app posts the quote as a silent, ongoing notification with public lock-screen visibility. It rotates on a schedule you choose, and the notification has a **Next quote** button.

## Features

- About 2,600 built-in quotes you can switch on and off by theme (motivation, thought-provoking, stoic & ancient wisdom, kindness & hope; the first two now include Marcus Aurelius, Epictetus, Seneca, Schopenhauer, Montaigne, Pascal, Emerson, Thoreau and more) or by author (Kahlil Gibran, Rainer Maria Rilke, Friedrich Nietzsche, Fyodor Dostoevsky, Leo Tolstoy, Franz Kafka, Plato, Lao Tzu, Søren Kierkegaard, Albert Camus, Hermann Hesse, Confucius, the Dhammapada, Rumi, Omar Khayyam, Sun Tzu, Rabindranath Tagore, Albert Einstein, Haruki Murakami, William Shakespeare, Walt Whitman, Emily Dickinson, William Blake, Leopardi, Baudelaire, the Bhagavad Gita, the Upanishads, Viktor Frankl, Pessoa, Borges, Cioran, Simone Weil, Neruda, Mary Oliver, Szymborska, Kundera, Sartre, Kabir, Ecclesiastes & Job, Zhuangzi, Melville, Turgenev, Chekhov, the Qur'an), plus your own. Rotation never repeats a quote until it has shown all the enabled ones. Sources and translation notes are in [SOURCES.md](SOURCES.md).
- Choose the font (six built-in Android families), size and text color of the quote. The notification uses a custom layout for this; Android still draws the background, so colors can clash with your light or dark theme ("System default" always works).
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

Needs JDK 17+ and the Android SDK (API 35). The GitHub Actions workflow builds the APK on every push, uploads it as an artifact, and publishes it to the [`latest` release](../../releases/tag/latest), which is the easiest place to download it on a phone. Builds share one debug key, so a new APK installs over the old one and keeps your quotes and settings.

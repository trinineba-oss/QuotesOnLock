# Publishing on Google Play

How the app is built for Google Play, and the steps that only the developer can take (they need your Google account and private keys).

## Two builds from one codebase

The app has two build flavors (`app/build.gradle.kts`):

| | GitHub build | Play build |
|---|---|---|
| Gradle task | `assembleGithubFullRelease` (APK) | `bundlePlayFullRelease` (App Bundle) |
| Updates | its own updater, from GitHub releases | Google Play |
| Internet access | yes (only to check GitHub for updates) | **none** |
| Install-packages permission | yes | no |
| Signed with | the shared debug key (so it installs over earlier builds) | your Play **upload key** |

A second dimension, *edition*, has one value today (`full`). A lite edition is added there later: a `lite` flavor with its own `applicationIdSuffix` (for example `.lite`), its own source set for anything it leaves out, and `BuildConfig.LITE` to switch features off in shared code.

Both builds target Android 16 (API 36), which Play requires for new apps and updates from 31 August 2026.

Every push builds both. The Play bundle is attached to the CI run as the artifact **quotes-on-lock-play-bundle**.

## 1. Create your upload key (once, on your own computer)

You need the JDK's `keytool` (it comes with Android Studio). Keep the file and passwords somewhere safe and backed up; never commit them.

```
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

## 2. Give CI the key (as repository secrets)

On GitHub: the repository › Settings › Secrets and variables › Actions › New repository secret. Add four:

- `UPLOAD_KEYSTORE_B64`: the keystore file as base64 (`base64 -w0 upload.jks` on Linux, `base64 -i upload.jks` on a Mac)
- `UPLOAD_KEYSTORE_PASSWORD`: the keystore password
- `UPLOAD_KEY_ALIAS`: `upload` (or the alias you chose)
- `UPLOAD_KEY_PASSWORD`: the key password

From the next push, the Play bundle is signed with this key. Until then it is signed with the debug key, which Play refuses.

## 3. Create the app in Play Console

1. Create a developer account at play.google.com/console (a one-time fee). New personal accounts must, under Google's current rules, run a closed test with at least 12 testers for 14 days before going to production (check Play Console for the exact requirement).
2. Create the app: name *Quotes on Lock*, app (not game), free.
3. Accept **Play App Signing** (Google keeps the final signing key; you upload with your upload key).
4. Upload the bundle from CI to **Testing › Internal testing**, add yourself and a few friends as testers, and install it from the Play link.

## 4. App content forms (Policy › App content)

- **Privacy policy:** a public web address for `docs/privacy-policy.md`. The simplest is GitHub Pages: repository › Settings › Pages › Deploy from a branch › your main branch, `/docs` folder. The policy is then at `https://<your-user>.github.io/<repo>/privacy-policy`.
- **Data safety:** "No data collected" and "No data shared". The Play build has no internet permission. Backups go only where the user saves them. No encryption-in-transit question applies, because nothing is transmitted.
- **Ads:** no ads.
- **Content rating:** fill in the questionnaire honestly. The app has quotations and myths that mention death, war and grief, and no violence, sexual content, gambling or user-to-user interaction.
- **Target audience:** 13 and over (it is not designed for children).
- **Health apps:** if asked, it is a general wellness app (breathing, calm sounds, reflection). It is not a medical device and gives no diagnosis or treatment. Solace says so in the app.
- **News app, government app, financial features:** no.

## 5. Store listing

- Short description (80 characters) and full description.
- App icon 512 × 512, feature graphic 1024 × 500.
- At least 2 phone screenshots (8 is best); 7-inch and 10-inch tablet screenshots help on tablets.
- Category: Lifestyle (or Books & Reference). Contact email: required by Play and shown publicly.

The screenshots can be rendered from the app with the Robolectric setup used during development; ask Claude for a set.

## Before you submit: decisions still open

- **Modern authors.** Short excerpts from authors still in copyright (Camus, Murakami, Morrison and others) are the main legal risk on a public store. Options: ship them switched off by default, leave them out of the Play build, or keep them as brief credited quotation.
- **Crisis lines.** Add emergency and helpline numbers for the countries you publish in to "It's too much right now" and My lifeline.
- **Test on real phones.** At least one older budget phone, one recent phone and a tablet, through the internal testing track.

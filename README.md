# Weight & Glucose Tracker

Weight & Glucose Tracker is an Android app that records weight, waist measurement and blood glucose level (BGL), and works out each insulin dose from the sliding scale on a doctor's order. It was designed for people aged 60 and over, so every screen uses large text and clearly labelled buttons, and each check is a short, guided series of steps.

## What the app does

- **Guided checks.** The home screen always shows what to do next. A check asks for the BGL reading, shows the dose from the sliding scale, and records whether the dose was given. It then asks about any other medicines due at that time, and offers to add a note for the doctor. Numbers are typed on a large keypad that stays on screen, like a phone keyboard, on small phones, at the largest text size and with the phone turned sideways.
- **Your prescription as a plan.** The sliding scale, the check times, other medicines (such as a premixed insulin or tablets) and goals are entered once from the doctor's order. A personal identification number (PIN) can protect the plan. Each change is saved as a new version, and earlier records keep the version that was in place at the time.
- **Safety prompts.** The app asks for confirmation when a dose is different from the prescription, when it falls outside the times on the order, or when a dose has already been recorded. A low reading shows what to do instead of a dose, with buttons to call 000 or a support person, and asks what was had to bring the level up. When low readings happen on 2 or more occasions in a week, the home screen suggests telling the doctor or diabetes educator.
- **Weight and waist.** Progress is shown against the starting weight and the goal, with the change since the last weigh-in.
- **Logbook and progress.** A calendar shows each day in green when everything was recorded, yellow when some of it was, and red when nothing was. Records can be viewed by day, changed or deleted, and shown as charts. A record that was forgotten can be added afterwards, with the time it happened, and a deleted record can be put back with Undo.
- **Reminders.** A reminder arrives at each check time, unless that check has already been recorded.
- **Export for the doctor.** A PDF report or a spreadsheet (CSV) can be shared by email or another app.
- **Backup and restore.** Records are part of the phone's Google backup when it is turned on. A backup file, with an optional password, can be saved to the phone or to cloud storage such as Google Drive, or shared. A new phone can restore from it on the first screen. A monthly reminder suggests saving a fresh backup file.
- **Optional assistant.** Gemma 4, a language model that runs on the phone, can summarise records and answer questions about them. It does not give medical advice or work out doses.
- **Your name.** An optional first and family name personalises the greeting and appears on reports for the doctor.
- **Display and accessibility.** Five text sizes from Small to Largest, maximum contrast, a dark theme, simple mode, read-aloud results with a sample of each speed, and options to hide icons or extra explanations. The check times can be called Breakfast, Lunch, Dinner and Bedtime, or Morning, Afternoon, Evening and Night.
- **Voices.** Results are read aloud in the phone's default voice, or another voice chosen in **Settings › Display and accessibility › Choose a voice**. The **Read this aloud** button changes to **Stop reading aloud** while a result is being read, and leaving the screen also stops the voice. Optional natural voices (a 98 MB download, with American and British accents) sound more like a person speaking. The first setup offers both downloads.

## Important

This app works out insulin doses from the sliding scale that you entered from your doctor's order. It does not give medical advice. Please speak with your doctor or diabetes educator if you have questions about your insulin. The advice shown for low and high readings is general information and should be checked with your doctor or diabetes educator.

This app is for personal and family use. It is not available on Google Play and is not a registered medical device.

## Installing the app

The app needs Android 12 or later. The assistant also needs a phone with 8 GB of memory or more.

1. Open the [latest release](../../releases/latest) on GitHub and download the `.apk` file, for example `weight-glucose-tracker-1.0.0.apk`. You can do this on the phone itself.
2. Open the file on the phone. Android will ask for permission to install apps from that source. Please allow it for this install only.
3. Open **Weight & Glucose Tracker** and follow the setup steps with the doctor's order in front of you.

New versions install over the old one, and your records are kept. Each release is signed with the same key, and its page lists the file's SHA-256 checksum. The changes in each version are in [CHANGELOG.md](CHANGELOG.md).

## The assistant

The first setup asks whether to download the assistant, and you can also set it up later in **Settings › Assistant**. The model is 2.6 GB. It downloads on Wi-Fi, or on mobile data if you agree after a warning that charges may apply. The app checks the download before it uses it. The app uses the internet only for this download and the optional natural voices. Your records stay on the phone, and the app does not send them anywhere. If you ask the assistant a question by speaking, the phone's own speech recognition turns your words into text, and it may use the internet to do that.

## Your records

Records are stored only on the phone. To keep them safe if you lose or replace your phone, please use **Settings › Backup and restore**. They are also included in the phone's own Google backup if that is turned on. You can share them with your doctor from **Settings › Export for your doctor**.

## For developers

The app is written in Kotlin with Jetpack Compose. The dose and schedule rules live in the pure-Kotlin `domain` module, and they are covered by unit tests. See [CLAUDE.md](CLAUDE.md) for build commands, the project layout, the safety rules and the copy rules.

```bash
./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The release signing key is kept outside the repository. Please keep a backup of it, because without it a new version cannot be installed over the old one.

To publish a release, set `versionCode` and `versionName` in `app/build.gradle.kts`, add a section for the version to `CHANGELOG.md`, and push a tag such as `v1.0.0`. CI builds the signed APK and creates the GitHub Release with the APK and its checksum. Each CI run also uploads a debug APK. It is a separate app, with its own records, so it is for testing only.

## Licence

This project is licensed under the GNU General Public License, version 3. Icons are Material Symbols from Google, used under the Apache License 2.0. The other software the app is built with, and its licences, are listed in the app under **Settings › About this app › Read the licences**.

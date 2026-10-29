# Changes

## 1.0.0-rc.2

### In the app

- The assistant now turns on when you download its model from Settings. The Assistant screen says what the assistant is waiting for, such as its download or being turned on, with a button for it. Once the assistant is ready, the question box is the first thing on the screen.
- Holding a finger on the logbook chart and sliding it sideways moves through the days, and the list follows. Sliding sideways on the full-screen chart moves a day at a time.
- Dragging up or down on the logbook's heading or chart scrolls the list, which helps with the phone turned sideways.
- **Settings › About this app** now links to the app on GitHub, with a button to share the app.

### For developers

- Version 1.0.0-rc.2 (versionCode 6), published as a pre-release.
- The assistant's page is `AssistantContent` with `AssistantActions`, so tests can show it in each state.

## 1.0.0-rc.1

### In the app

- **Logbook › List** now shows each check as one line, with its value in a pill that has the colour and icon of its status, under a chart of the days around it that follows the list as you scroll. Each day's heading stays at the top while its records scroll. Pressing a check shows its doses and notes.
- Pressing a day on the logbook chart turns the phone sideways to a full-screen chart of that day, by time of day, over your readings from the 30 days before. Earlier day and Later day change the day, and Back returns to the list at that day.
- LO and HI are now keys beside the digits on the blood glucose keypad, and the time of day is a button beside Back, so nothing is hidden under the keypad on a small phone. Like a typed number, LO or HI is saved when you press Next.
- A PIN that starts with 0, such as 0412, now unlocks your plan.
- Back on the first welcome screen now closes the app, instead of opening a home screen with no plan.
- Back now goes straight to the previous screen, without the preview that shrinks the window.
- The Medicines screen now counts only the days since your plan, or each medicine, started.
- Reminders now arrive on time while the phone is idle, including the reminder to check again 15 minutes after a low reading.
- Once a reading is recorded, the home screen and its reminder ask only for the medicines still due at that time, so there is no second finger-prick.
- The home screen now says when reminders cannot appear because notifications are turned off. Allow notifications opens the phone's settings when Android will no longer ask.
- After a low reading, a new reading that is back up now suggests some longer-acting carbohydrate.
- The assistant now gives the app's own answers to questions about doses, to signs of a low level, and to thoughts of suicide or self-harm. It is no longer given your sliding scale, so it cannot work out a dose.
- Setup and check-ins now carry on where they were if Android closes the app in the background.
- Setup now checks that the check times follow each other through the day, and that your low level is not above your target range.
- A damaged backup file is now refused before anything on the phone is replaced.
- Dates are now set with large Earlier and Later buttons instead of a calendar.
- Reminders and new records now follow the phone to a new time zone.
- LO and HI readings now show as LO and HI on the Progress screen and in the readout.
- The report and the Progress screen no longer show counts of nought, such as "not given 0 times".
- The monthly backup reminder now has its own notification setting.
- Screen readers now hear the 15-minute timer each minute, a download every tenth of the way, and the assistant's answer once it is complete.
- **Settings › About this app › Read the licences** lists the software the app is built with, and its licences.

### For developers

- Version 1.0.0-rc.1 (versionCode 5), published as a pre-release for the checks to do before 1.0.0.
- The release APK now leaves out the emulator libraries as intended, so it is about 81 MB instead of 135 MB. ABI filters are set per build type, because a build type's filters were being added to the default ones.
- A `v*` tag publishes the signed APK and its SHA-256 checksum as a GitHub Release. A version with a suffix, such as 1.0.0-rc.1, is published as a pre-release.
- CI checks that `strings.xml` matches `tools/strings_*.py`.
- Problem reports now include failures of the assistant, the export and saving a plan.

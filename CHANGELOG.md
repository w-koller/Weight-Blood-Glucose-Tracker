# Changes

## 1.0.0

### In the app

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

- Version 1.0.0 (versionCode 4).
- The release APK now leaves out the emulator libraries as intended, so it is about 81 MB instead of 135 MB. ABI filters are set per build type, because a build type's filters were being added to the default ones.
- A `v*` tag publishes the signed APK and its SHA-256 checksum as a GitHub Release.
- CI checks that `strings.xml` matches `tools/strings_*.py`.
- Problem reports now include failures of the assistant, the export and saving a plan.

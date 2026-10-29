# Weight & Glucose Tracker

An Android app for seniors (60+) that records weight, waist and blood glucose, works out the
sliding-scale insulin dose from the user's own prescription, and offers an optional on-device
assistant (Gemma 4 E2B through LiteRT-LM).

## Build and test

The local toolchain lives outside the repo: JDK 17 at `~/.local/opt/jdk-17` and the Android SDK at
`~/Android/Sdk` (`local.properties` points there and is not committed). There is no emulator (no KVM).

```bash
export JAVA_HOME=~/.local/opt/jdk-17 ANDROID_HOME=~/Android/Sdk
./gradlew :domain:test                  # pure-Kotlin rules, fast
./gradlew :app:testDebugUnitTest        # Robolectric: repository, screen flows, export
./gradlew :app:lintDebug :app:assembleDebug
```

`ScreenFlowTest` and `ExportTest` save screenshots, report pages and a sample CSV to
`app/build/screenshots/`. Look at them after UI changes. `ScreenFlowTest` uses a very tall screen so
each step fits in one picture; `SmallScreenTest` checks number entry on a 360×640 phone, at the largest
text size and turned sideways, and saves `small_*.png`. Robolectric cannot run `PdfDocument`, so
report pages are drawn through `PageSink`.

Back uses the classic back event: `android:enableOnBackInvokedCallback="false"` in the manifest turns off the
predictive-back preview that shrinks the window. Android calls this opt-out temporary, so check that it still
applies before raising `targetSdk` above 36.

CI (`.github/workflows/android.yml`) runs the same tasks and uploads the debug APK. It also builds a
signed release APK when the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`
secrets are set. The release keystore lives at `~/.android-keys/` and must never be committed. A `v*` tag
that matches `versionName` publishes the signed APK and its checksum as a GitHub Release, with the notes from
that version's section of `CHANGELOG.md`. CI also fails when `strings.xml` does not match `tools/strings_*.py`.

About › Licences reads `assets/licences/`. The build copies the licence and notice files out of the LiteRT-LM
and Commons Compress archives into it (`collect<Variant>Licences`), and `app/src/main/assets/licences/` holds
the ONNX Runtime licence, which no archive carries. Please add a library to the list in `LicencesScreens.kt`
when it is added to the app.

## Layout

- `domain/`: all dose and schedule rules, with no Android code. `SlidingScale`, `DoseAdvisor`,
  `OverrideCheck`, `SlotResolver`, `NextStepEngine`, `ProgressCalculator`, `GlucoseStats`, `ProgressModel`, `LowPatterns`,
  `DailyLog`, `Logbook`, `AssistantGuard` and `PromptBuilder`. Every rule change needs a test here.
  `Logbook` turns each day into the lines of the logbook list and the points of the chart above it, and
  `DayCharts` places one day's readings, and those of the 30 days before, by time of day for the full-screen chart.
  `ProgressModel` is the one source for the Progress screen's charts, what TalkBack reads for each day,
  the PDF report's summary and chart, the readout, and the facts given to the assistant.
- `app/data`: Room (versioned plans and records) and DataStore settings.
- `app/ui`: Compose screens. `ui/components` holds the shared senior-friendly building blocks.
- `app/reminders`, `app/export`, `app/ai`: alarms, CSV/PDF export, model download and inference.
- `app/download`: `VerifiedDownload`, the shared Wi-Fi-only, checksum-checked download used by the assistant model and the natural voices.
- `app/tts`: `Speaker` (phone voices) and `NaturalVoices` (Kokoro through sherpa-onnx). The sherpa-onnx AAR is not on Maven; the `downloadSherpaOnnx` Gradle task fetches it and checks its SHA-256.

## Safety rules

- Doses come only from `DoseAdvisor` and the user's plan. The language model never calculates or
  suggests a dose; `AssistantGuard` sends dose questions and urgent symptoms to fixed answers and
  rejects model answers that mention insulin amounts not in the records.
- Nothing prescription-specific is hard-coded for one person. The sliding scale, medicines, times and
  goals are all entered as a plan. Changes create a new plan version, and old records keep theirs.
- Glucose is stored in tenths of mmol/L, weight in grams and waist in millimetres, never as floats.
- Room has no destructive migration fallback. Add a migration and commit the schema in
  `app/schemas/` when the database changes.
- No personal details from the paper forms (names, Medicare numbers, addresses) go into the repo.

## Copy

All user-facing text is in `tools/strings_*.py`; run `python3 tools/gen_strings.py` to regenerate
`app/src/main/res/values/strings.xml`. Do not edit `strings.xml` by hand.

Copy follows the product-UI rules in `/home/william/informed-trader/VOICE.md`:

- Say plainly what the thing is, then what the reader can do, then what happens next, in complete
  sentences.
- Put the main clause first and the condition after it. Headings name the thing, without conditions.
- Write "Please" in instructions and errors. No clipped or inverted phrasing, and no colon-led openers.
- Spell a term out once with its abbreviation, as in "Blood glucose level (BGL)".
- Use Australian spelling everywhere (recognise, colour, summarise, towards).
- Inside the app it is fine to name the model ("Gemma 4") and its state ("Loading the model.").

## UI rules for this audience

Large, always-labelled buttons (`BigButton`, at least 72 dp tall). One question per step
(`StepScaffold`). Number entry uses `NumberEntryScaffold`, which keeps the value, keypad and main
button on screen like a keyboard. Its scrolling header holds words only: buttons go in `wordKeys`,
beside the digits (LO and HI), or in `headerAction`, beside Back (the time of day), so the keypad never
hides them. Extra buttons go in `StepScaffold`'s `moreActions`, which move into the content on short
screens. Slot names come from `Slot.label()` and `Slot.phrase()`, which follow
the user's choice of meal names or times of day. No gesture-only or long-press-only actions and no
timeouts. Status is shown with an icon and a word, not colour alone. Respect `LocalUiPrefs` (icons,
helper text, simple mode, reduce motion, read aloud, slot names) in new components.

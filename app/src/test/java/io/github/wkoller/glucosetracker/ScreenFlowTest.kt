package io.github.wkoller.glucosetracker

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.glucosetracker.ai.ModelState
import io.github.wkoller.glucosetracker.data.db.toEntities
import io.github.wkoller.glucosetracker.data.prefs.Settings
import io.github.wkoller.glucosetracker.domain.assistant.AssistantGuard
import io.github.wkoller.glucosetracker.domain.assistant.QuestionKind
import io.github.wkoller.glucosetracker.domain.model.BodyMeasurement
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.MeterFlag
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.domain.model.SlotNaming
import io.github.wkoller.glucosetracker.domain.progress.ProgressModelBuilder
import io.github.wkoller.glucosetracker.tts.LocalSpeaker
import io.github.wkoller.glucosetracker.tts.Speaker
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.assistant.AssistantActions
import io.github.wkoller.glucosetracker.ui.assistant.AssistantContent
import io.github.wkoller.glucosetracker.ui.assistant.AssistantScreen
import io.github.wkoller.glucosetracker.ui.assistant.PERIOD_SUGGESTIONS
import io.github.wkoller.glucosetracker.ui.checkin.CheckInScreen
import io.github.wkoller.glucosetracker.ui.home.HomeActions
import io.github.wkoller.glucosetracker.ui.home.HomeScreen
import io.github.wkoller.glucosetracker.ui.nav.AppNavHost
import io.github.wkoller.glucosetracker.ui.plan.PlanSetupScreen
import io.github.wkoller.glucosetracker.ui.progress.ProgressContent
import io.github.wkoller.glucosetracker.ui.progress.ProgressScreen
import io.github.wkoller.glucosetracker.ui.progress.ProgressUi
import io.github.wkoller.glucosetracker.ui.theme.TrackerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Drives the real screens on the JVM and saves a picture of each step to build/screenshots, so the
 * layout can be reviewed without a phone or emulator.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class ScreenFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: TrackerApp get() = ApplicationProvider.getApplicationContext()
    private val repository get() = app.container.repository

    // DataStore keeps one instance for the whole test run, so settings are reset before each test.
    @Before
    fun resetSettings() = runBlocking { app.container.settingsStore.update { Settings() } }

    private fun show(settings: Settings = Settings(setupDone = true), speaker: Speaker? = null, content: @Composable () -> Unit) {
        compose.setContent {
            TrackerTheme(settings) {
                CompositionLocalProvider(LocalSpeaker provides speaker) { Surface { content() } }
            }
        }
    }

    /**
     * Waits for work that finishes on a background thread and then resumes on the main thread, such as
     * a database or settings write. Compose's own wait does not let the main looper run that resumption.
     */
    private fun waitForBackgroundWork(condition: () -> Boolean) = compose.waitUntil(10_000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(20)
        condition()
    }

    private fun waitFor(text: String) = compose.waitUntil(timeoutMillis = 10_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun click(text: String) {
        waitFor(text)
        val node = compose.onAllNodesWithText(text).onFirst()
        // Buttons in the bottom bar and in dialogs are not inside a scrolling column.
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    private fun type(value: String) = value.forEach { compose.onNodeWithTag("key_$it").performClick() }

    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun seedPlan() = runBlocking {
        repository.savePlanVersion(TestPlans.example())
        app.container.settingsStore.update { it.copy(setupDone = true) }
    }

    /** The time of day sits beside Back as a button that shows the current choice, such as Breakfast. */
    private fun chooseSlot(slotName: String) {
        compose.onNodeWithTag("time_of_day").performClick()
        compose.waitForIdle()
        click(slotName)
    }

    @Test
    fun `first-run setup creates a plan`() {
        var finished = false
        // As on a new phone, where notifications are off until the app is allowed to show them.
        org.robolectric.Shadows.shadowOf(app.getSystemService(android.app.NotificationManager::class.java)).setNotificationsEnabled(false)
        show(Settings()) { PlanSetupScreen(onClose = {}, onFinished = { finished = true }) }
        waitFor("Welcome")
        shot("setup_01_welcome")
        click("Next")
        waitFor("About you")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Mary")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Smith")
        shot("setup_02_about_you")
        click("Next")
        waitFor("Your prescription")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("NovoRapid")
        shot("setup_03_prescription")
        click("Next")
        waitFor("Start with an empty table")
        shot("setup_04_scale_start")
        click("Fill in a common scale")
        waitFor("Above 20")
        shot("setup_05_scale_table")
        click("Next")
        click("I have checked this against my doctor\u2019s order.")
        shot("setup_06_scale_check")
        click("Next")
        waitFor("Check times")
        click("Next")
        click("Add a medicine")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("NovoMix 30")
        click("Insulin, measured in units")
        compose.onAllNodes(hasSetTextAction())[2].performTextInput("30")
        compose.onAllNodes(hasSetTextAction())[4].performTextInput("26")
        click("Save this medicine")
        waitFor("NovoMix 30")
        click("Next")
        waitFor("Your goals")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("115")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("100")
        click("Next")
        waitFor("Your target range")
        click("Next")
        click("Continue without a PIN")
        waitFor("Allow notifications")
        click("Next")
        waitFor("Would you like to download the assistant now?")
        shot("setup_07_assistant")
        click("Not now")
        waitFor("Would you like to download the natural voices now?")
        shot("setup_07b_voices")
        click("Not now")
        waitFor("Please review your plan")
        shot("setup_08_review")
        click("Start my plan")
        // The wizard reports that it has finished only after the plan and the settings are both saved.
        waitForBackgroundWork { finished }
        val plan = runBlocking { repository.currentPlan()!! }
        assertEquals("NovoRapid", plan.correctionInsulin)
        assertEquals(6, plan.scale.bands.size)
        assertEquals(mapOf(Slot.BREAKFAST to 30.0, Slot.DINNER to 26.0), plan.medicines.single().doses)
        assertEquals(115_000, plan.goals.startWeightGrams)
        val settings = runBlocking { app.container.settingsStore.current() }
        assertEquals("Mary Smith", settings.fullName)
        assertEquals(false, settings.assistantEnabled)
    }

    @Test
    fun `Back on the first welcome screen closes the app`() {
        var activity: Activity? = null
        show(Settings()) {
            activity = LocalActivity.current
            AppNavHost(startWithSetup = true, openSlot = null, onSlotOpened = {}, openBackup = false, onBackupOpened = {})
        }
        waitFor("Welcome")
        // Before, Back left the setup for a home screen with no plan, where a check-in never finished loading.
        compose.runOnUiThread { (activity as ComponentActivity).onBackPressedDispatcher.onBackPressed() }
        assertTrue(activity!!.isFinishing)
    }

    private fun openScaleStep() {
        click("Next")
        click("Next")
        waitFor("Your prescription")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("NovoRapid")
        click("Next")
        waitFor("Start with an empty table")
    }

    @Test
    fun `an empty scale is filled in on the keypad and errors name the problem`() {
        show(Settings()) { PlanSetupScreen(onClose = {}, onFinished = {}) }
        openScaleStep()
        click("Start with an empty table")
        // One number at a time, named by its row, as on the paper order.
        waitFor("Top of the range that starts at 0 mmol/L")
        waitFor("Row 1 of 6")
        type("10")
        click("Next")
        waitFor("Units of NovoRapid for 0 to 10 mmol/L")
        type("0")
        click("Next")
        waitFor("Top of the range that starts at 10.1 mmol/L")
        type("8")
        click("Next")
        waitFor("Please enter a number more than 10, the top of the row above.")
        shot("setup_05_scale_keypad")
        // Back keeps the numbers already entered, and the table says what is still missing.
        click("Back")
        waitFor("0 to 10 mmol/L, 0 units")
        click("Next")
        waitFor("Please fill in every box in the table.")
        shot("setup_09_scale_errors")
    }

    @Test
    fun `a row of the scale is changed on the keypad`() {
        show(Settings()) { PlanSetupScreen(onClose = {}, onFinished = {}) }
        openScaleStep()
        click("Fill in a common scale")
        click("10.1 to 12 mmol/L, 2 units")
        waitFor("Top of the range that starts at 10.1 mmol/L")
        repeat(2) { compose.onNodeWithTag("key_back").performClick() }
        type("13")
        click("Next")
        waitFor("Units of NovoRapid for 10.1 to 13 mmol/L")
        compose.onNodeWithTag("key_back").performClick()
        type("3")
        click("Done")
        waitFor("10.1 to 13 mmol/L, 3 units")
        waitFor("13.1 to 14 mmol/L")
    }

    @Test
    fun `breakfast check-in records the dose and the morning medicines`() {
        seedPlan()
        show { CheckInScreen(onClose = {}, onMeasure = {}, onCheckIn = {}) }
        waitFor("Please enter the reading")
        chooseSlot("Breakfast")
        type("13.7")
        shot("checkin_01_entry")
        click("Next")
        waitFor("Your prescription says to give 4 units of NovoRapid.")
        shot("checkin_02_result")
        click("I gave 4 units")
        waitFor("NovoMix 30 is due before breakfast.")
        shot("checkin_03_premix")
        click("I gave 30 units")
        waitFor("I took all of these")
        shot("checkin_04_tablets")
        click("I took all of these")
        waitFor("All done")
        shot("checkin_05_done")

        val today = runBlocking { repository.logicalToday(repository.currentPlan()) }
        val doses = runBlocking { repository.dosesBetween(today, today) }
        val correction = doses.single { it.kind == DoseKind.CORRECTION }
        assertEquals(4.0, correction.givenAmount!!, 0.0)
        assertEquals(3, doses.count { it.kind == DoseKind.FIXED && it.status == DoseStatus.GIVEN })
    }

    @Test
    fun `a result read aloud can be stopped`() {
        seedPlan()
        // Robolectric's phone voice never finishes starting up, so the text stays in the middle of being read.
        val speaker = app.container.speaker
        show(Settings(setupDone = true, readAloud = true), speaker) { CheckInScreen(onClose = {}, onMeasure = {}, onCheckIn = {}) }
        waitFor("Please enter the reading")
        chooseSlot("Breakfast")
        type("13.7")
        click("Next")
        // The result is read out as soon as it appears, so its button offers to stop it.
        waitFor("Stop reading aloud")
        shot("checkin_40_stop_reading")
        click("Stop reading aloud")
        waitFor("Read this aloud")
        assertNull(speaker.speaking.value)
        click("Read this aloud")
        waitFor("Stop reading aloud")
        // Moving on to the next step stops the reading.
        click("I gave 4 units")
        waitFor("NovoMix 30 is due before breakfast.")
        assertNull(speaker.speaking.value)
        waitFor("Read this aloud")
    }

    @Test
    fun `a bedtime dose asks for confirmation`() {
        seedPlan()
        show { CheckInScreen(onClose = {}, onMeasure = {}, onCheckIn = {}) }
        waitFor("Please enter the reading")
        chooseSlot("Bedtime")
        type("14.4")
        click("Next")
        waitFor("This time is not on your order")
        shot("checkin_10_bedtime_notice")
        click("I gave 6 units")
        waitFor("Please confirm this dose")
        shot("checkin_11_bedtime_confirm")
        click("Record 6 units")
        waitFor("All done")
        val today = runBlocking { repository.logicalToday(repository.currentPlan()) }
        assertEquals(6.0, runBlocking { repository.dosesBetween(today, today) }.single().givenAmount!!, 0.0)
    }

    @Test
    fun `a different amount asks for confirmation`() {
        seedPlan()
        show { CheckInScreen(onClose = {}, onMeasure = {}, onCheckIn = {}) }
        waitFor("Please enter the reading")
        chooseSlot("Lunch")
        type("13.7")
        click("Next")
        click("I gave a different amount")
        type("6")
        click("Next")
        waitFor("You have entered 6 units.")
        shot("checkin_20_different_amount")
    }

    @Test
    fun `a low reading shows what to do instead of a dose`() {
        seedPlan()
        show { CheckInScreen(onClose = {}, onMeasure = {}, onCheckIn = {}) }
        waitFor("Please enter the reading")
        chooseSlot("Lunch")
        type("3.6")
        click("Next")
        waitFor("Please do not give NovoRapid now.")
        shot("checkin_30_low")
        assertTrue(compose.onAllNodesWithText("I gave", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `LO and HI are keys on the keypad and the reading is saved with Next`() {
        seedPlan()
        show { CheckInScreen(onClose = {}, onMeasure = {}, onCheckIn = {}) }
        waitFor("Please enter the reading")
        chooseSlot("Lunch")
        val today = runBlocking { repository.logicalToday(repository.currentPlan()) }
        // The LO key, and the LO in the reading once it is pressed.
        fun loTexts() = compose.onAllNodesWithText("LO").fetchSemanticsNodes().size
        assertEquals(1, loTexts())

        compose.onNodeWithTag("key_lo").performClick()
        compose.waitForIdle()
        assertEquals(2, loTexts())
        // The reading reads as the meter does, for TalkBack too. The key with the same words is the one that can be pressed.
        compose.onNode(hasContentDescription("My meter shows LO") and !androidx.compose.ui.test.hasClickAction()).assertExists()
        assertTrue("LO is not saved until Next is pressed", runBlocking { repository.readingsBetween(today, today) }.isEmpty())
        shot("checkin_07_meter_lo")

        // A digit replaces the word, as it would a mistyped number.
        type("5")
        assertEquals(1, loTexts())
        compose.onNodeWithTag("key_hi").performClick()
        compose.onNodeWithTag("key_lo").performClick()
        click("Next")
        waitFor("Please do not give NovoRapid now.")
        waitForBackgroundWork { runBlocking { repository.readingsBetween(today, today) }.isNotEmpty() }
        val saved = runBlocking { repository.readingsBetween(today, today) }.single()
        assertEquals(MeterFlag.LO, saved.meterFlag)
        assertEquals(Slot.LUNCH, saved.slot)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `the logbook list shows each check as a line and opens it for the details`() {
        seedPlan()
        val today = runBlocking { repository.logicalToday(repository.currentPlan()) }
        runBlocking { TestRecords.seedLogbook(repository, today) }
        var charted: LocalDate? = null
        show { io.github.wkoller.glucosetracker.ui.logbook.LogbookScreen(onBack = {}, onDay = {}, onChart = { charted = it }) }
        click("List")
        waitFor("Today, " + UiFormat.dayLong(today))
        shot("logbook_02_list")

        // Each check is one stop for TalkBack, with its time, value and status, and opens in place.
        val breakfast = hasContentDescription("Breakfast. 13.7 mmol/L, above your target range.", substring = true)
        compose.onNode(breakfast and hasContentDescription("4 units of NovoRapid, 30 units of NovoMix 30 and 2 other medicines.", substring = true)).performClick()
        waitFor("NovoRapid, 4 units given")
        waitFor("Spironolactone 25 mg, 1 tablet given")
        compose.onNodeWithText("See or change this day").assertExists()
        shot("logbook_03_details")

        // The chart has a stop for each of its days, which opens that day on the full-screen chart.
        val twoDaysAgo = today.minusDays(2)
        compose.onNode(hasContentDescription(UiFormat.dayLong(twoDaysAgo) + ". 3 readings", substring = true)).performClick()
        compose.waitForIdle()
        assertEquals(twoDaysAgo, charted)
    }

    /** Slides a finger along the list's chart from left to right, which shows earlier days. */
    private fun slideChartRight(share: Float) = compose.onNodeWithTag("logbook_chart").performTouchInput {
        swipe(start = androidx.compose.ui.geometry.Offset(width * 0.2f, centerY), end = androidx.compose.ui.geometry.Offset(width * (0.2f + share), centerY), durationMillis = 600)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `sliding a finger along the list's chart moves the list through the days`() {
        seedPlan()
        val today = runBlocking { repository.logicalToday(repository.currentPlan()) }
        runBlocking { TestRecords.seedLogbook(repository, today) }
        var charted: LocalDate? = null
        show { io.github.wkoller.glucosetracker.ui.logbook.LogbookScreen(onBack = {}, onDay = {}, onChart = { charted = it }) }
        click("List")
        waitFor("Today, " + UiFormat.dayLong(today))

        // A slide of about a day and a half to the right brings the day in the chart's middle, two days ago, to the top.
        slideChartRight(0.45f)
        compose.waitForIdle()
        val twoDaysAgo = today.minusDays(2)
        compose.onNodeWithText(UiFormat.dayLong(twoDaysAgo)).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Today, " + UiFormat.dayLong(today)).fetchSemanticsNodes().isEmpty())
        assertNull("A slide is not a press, so the full-screen chart stays closed", charted)
        shot("logbook_08_chart_slid")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xhdpi")
    fun `the list's chart slides through the days with the phone turned sideways`() {
        seedPlan()
        val today = runBlocking { repository.logicalToday(repository.currentPlan()) }
        runBlocking { TestRecords.seedLogbook(repository, today) }
        show { io.github.wkoller.glucosetracker.ui.logbook.LogbookScreen(onBack = {}, onDay = {}) }
        click("List")
        waitFor("Today, " + UiFormat.dayLong(today))
        slideChartRight(0.45f)
        compose.waitForIdle()
        compose.onNodeWithText(UiFormat.dayLong(today.minusDays(2))).assertIsDisplayed()

        // Dragging up on the chart slides the heading away, so the list has the room under the chart.
        compose.onNodeWithTag("logbook_chart").performTouchInput { swipeUp(startY = bottom * 0.9f, endY = top, durationMillis = 400) }
        compose.waitForIdle()
        compose.onNodeWithText("Back").assertIsNotDisplayed()
        shot("logbook_09_chart_slid_sideways")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `a day pressed on the chart opens sideways on a full-screen chart, and Back returns to it in the list`() {
        seedPlan()
        val today = runBlocking { repository.logicalToday(repository.currentPlan()) }
        runBlocking { TestRecords.seedLogbook(repository, today) }
        lateinit var activity: Activity
        lateinit var nav: androidx.navigation.NavHostController
        show {
            activity = LocalActivity.current!!
            nav = androidx.navigation.compose.rememberNavController()
            AppNavHost(startWithSetup = false, openSlot = null, onSlotOpened = {}, openBackup = false, onBackupOpened = {}, navController = nav)
        }
        compose.runOnUiThread { nav.navigate(io.github.wkoller.glucosetracker.ui.nav.Routes.LOGBOOK) }
        click("List")
        waitFor("Today, " + UiFormat.dayLong(today))
        val orientation = activity.requestedOrientation

        val twoDaysAgo = today.minusDays(2)
        compose.onNodeWithTag("chart_day_$twoDaysAgo").performClick()
        waitFor("Earlier day")
        compose.onNodeWithText(UiFormat.dayLong(twoDaysAgo)).assertIsDisplayed()
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.requestedOrientation)
        compose.onNode(hasContentDescription("HI (above 33.3 mmol/L), above your target range.", substring = true)).assertExists()

        // Earlier day moves the chart, and Back opens the list at the day the chart was showing.
        val threeDaysAgo = today.minusDays(3)
        click("Earlier day")
        waitFor(UiFormat.dayLong(threeDaysAgo))
        click("Back")
        waitFor("List")
        compose.waitForIdle()
        compose.onNodeWithText(UiFormat.dayLong(threeDaysAgo)).assertIsDisplayed()
        assertTrue("The list opens at the day the chart was showing", compose.onAllNodesWithText("Today, " + UiFormat.dayLong(today)).fetchSemanticsNodes().isEmpty())
        assertEquals(orientation, activity.requestedOrientation)
        shot("logbook_04_back_from_chart")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xhdpi")
    fun `the full-screen chart shows the day's values over the days before`() {
        seedPlan()
        val today = runBlocking { repository.logicalToday(repository.currentPlan()) }
        runBlocking {
            TestRecords.seedLogbook(repository, today)
            TestRecords.seedHistory(repository, today)
        }
        val nav = showApp()
        val yesterday = today.minusDays(1)
        compose.runOnUiThread { nav.navigate(io.github.wkoller.glucosetracker.ui.nav.Routes.logbookChart(yesterday.toString())) }
        waitFor("Earlier day")

        // TalkBack hears the day, then each value in time order.
        compose.onNode(hasContentDescription(UiFormat.dayLong(yesterday) + ". 5 readings", substring = true) and hasContentDescription("The small dots show", substring = true)).assertExists()
        compose.onNode(hasContentDescription("LO (below 1.1 mmol/L), low.", substring = true)).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Later day").fetchSemanticsNodes().isNotEmpty())
        shot("logbook_06_day_chart")

        compose.onNode(hasContentDescription("Dinner. 15.2 mmol/L", substring = true)).performClick()
        waitFor("NovoRapid, 6 units given")
        shot("logbook_07_day_chart_details")
        click("Close")
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("NovoRapid, 6 units given").fetchSemanticsNodes().isEmpty())

        // Sliding a finger sideways moves through the days: to the right for the day before, to the left for the day after.
        fun slide(from: Float, to: Float) = compose.onNodeWithTag("day_chart").performTouchInput {
            swipe(start = androidx.compose.ui.geometry.Offset(width * from, centerY), end = androidx.compose.ui.geometry.Offset(width * to, centerY), durationMillis = 500)
        }
        slide(0.3f, 0.6f)
        waitFor(UiFormat.dayLong(today.minusDays(2)))
        slide(0.6f, 0.3f)
        waitFor(UiFormat.dayLong(yesterday))
        assertTrue(compose.onAllNodesWithText("NovoRapid, 6 units given").fetchSemanticsNodes().isEmpty())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `the logbook list in the dark theme`() {
        seedPlan()
        val today = runBlocking { repository.logicalToday(repository.currentPlan()) }
        runBlocking { TestRecords.seedLogbook(repository, today) }
        show(Settings(setupDone = true, theme = io.github.wkoller.glucosetracker.data.prefs.ThemeChoice.DARK)) {
            io.github.wkoller.glucosetracker.ui.logbook.LogbookScreen(onBack = {}, onDay = {})
        }
        click("List")
        waitFor("Today, " + UiFormat.dayLong(today))
        shot("logbook_05_dark")
    }

    @Test
    fun `the assistant shows a readout before the model is set up`() {
        seedPlan()
        runBlocking {
            val plan = repository.currentPlan()!!
            val today = repository.logicalToday(plan)
            repository.addReading(GlucoseReading(0, plan.id, repository.instant(), today, Slot.BREAKFAST, 137, null, null))
            repository.addReading(GlucoseReading(0, plan.id, repository.instant(), today, Slot.LUNCH, 36, null, null))
        }
        show { io.github.wkoller.glucosetracker.ui.assistant.AssistantScreen(onBack = {}, onSetUp = {}, onCheckIn = {}) }
        waitFor("You have recorded 2 readings")
        waitFor("The assistant is not set up")
        shot("assistant_01_readout")
    }

    @Test
    fun `assistant settings offer the model download`() {
        seedPlan()
        show { io.github.wkoller.glucosetracker.ui.assistant.AssistantSettingsScreen(onBack = {}) }
        waitFor("Download the model")
        shot("assistant_02_settings")
    }

    @Test
    fun `the assistant puts the question box first once it is ready`() {
        seedPlan()
        val context = runBlocking { app.container.assistant.context() }!!
        var ui by mutableStateOf(io.github.wkoller.glucosetracker.ui.assistant.AssistantUi(context = context, modelState = ModelState.Ready, enabled = true))
        var asked: String? = null
        show {
            AssistantContent(
                ui,
                AssistantActions(back = {}, setUp = {}, checkIn = {}, editQuestion = { ui = ui.copy(question = it) }, ask = { asked = it }),
            )
        }
        waitFor("Ask a question")
        compose.onNode(hasSetTextAction()).performTextInput("How were my readings before dinner?")
        click("Ask")
        assertEquals("How were my readings before dinner?", asked)
        waitFor("Questions you can ask")
        shot("assistant_04_ready")
    }

    @Test
    fun `a downloaded assistant that is turned off offers to turn it on`() {
        seedPlan()
        val context = runBlocking { app.container.assistant.context() }!!
        var turnedOn = false
        show {
            AssistantContent(
                io.github.wkoller.glucosetracker.ui.assistant.AssistantUi(context = context, modelState = ModelState.Ready, enabled = false),
                AssistantActions(back = {}, setUp = {}, checkIn = {}, turnOn = { turnedOn = true }),
            )
        }
        waitFor("The assistant is turned off")
        assertTrue(compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
        shot("assistant_05_turned_off")
        click("Turn on the assistant")
        assertTrue(turnedOn)

        // The button turns the assistant on in Settings.
        val vm = io.github.wkoller.glucosetracker.ui.assistant.AssistantViewModel(app.container)
        vm.turnOn()
        waitForBackgroundWork { runBlocking { app.container.settingsStore.settings.first() }.assistantEnabled }
    }

    @Test
    fun `the assistant shows its download while the model downloads`() {
        seedPlan()
        val context = runBlocking { app.container.assistant.context() }!!
        var opened = false
        show {
            AssistantContent(
                io.github.wkoller.glucosetracker.ui.assistant.AssistantUi(context = context, modelState = ModelState.Downloading(650_000_000, 2_600_000_000), enabled = true),
                AssistantActions(back = {}, setUp = { opened = true }, checkIn = {}),
            )
        }
        waitFor("25% of the model has downloaded.")
        shot("assistant_06_downloading")
        click("See the download")
        assertTrue(opened)
    }

    @Test
    fun `downloading the model turns on the assistant`() {
        seedPlan()
        val directory = app.getExternalFilesDir(io.github.wkoller.glucosetracker.ai.ModelManager.MODEL_DIR)!!.apply { mkdirs() }
        org.robolectric.shadows.ShadowStatFs.registerStats(directory, 2_000_000, 2_000_000, 2_000_000)
        show { io.github.wkoller.glucosetracker.ui.assistant.AssistantSettingsScreen(onBack = {}) }
        click("Download the model")
        waitForBackgroundWork { runBlocking { app.container.settingsStore.settings.first() }.assistantEnabled }
        app.container.models.cancelDownload()
    }

    @Test
    fun `About links to the app on GitHub and shares it`() {
        lateinit var activity: Activity
        show {
            activity = LocalActivity.current!!
            io.github.wkoller.glucosetracker.ui.settings.AboutScreen(onBack = {})
        }
        waitFor("github.com/w-koller/Weight-Blood-Glucose-Tracker")
        compose.onNodeWithText("Share the app").performScrollTo()
        shot("about_03_github")

        click("Open the GitHub page")
        val opened = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_VIEW, opened.action)
        assertEquals(io.github.wkoller.glucosetracker.ui.settings.PROJECT_URL, opened.dataString)

        click("Share the app")
        val chooser = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
        assertEquals(android.content.Intent.ACTION_SEND, send.action)
        assertTrue(send.getStringExtra(android.content.Intent.EXTRA_TEXT)!!.contains(io.github.wkoller.glucosetracker.ui.settings.PROJECT_URL))
    }

    @Test
    fun `dose questions are answered by the app, not the model`() {
        seedPlan()
        val service = app.container.assistant
        val replies = runBlocking {
            val context = service.context()!!
            listOf("How many units of NovoRapid should I take?", "I have chest pain", "I feel shaky and sweaty", "I want to end my life").map { question ->
                val collected = mutableListOf<io.github.wkoller.glucosetracker.ai.AssistantReply>()
                service.ask(context, question, emptyList()).collect { collected += it }
                collected
            }
        }
        assertEquals(listOf(io.github.wkoller.glucosetracker.ai.AssistantReply.DoseQuestion), replies[0])
        assertEquals(listOf(io.github.wkoller.glucosetracker.ai.AssistantReply.Urgent), replies[1])
        assertEquals(listOf(io.github.wkoller.glucosetracker.ai.AssistantReply.Hypo), replies[2])
        assertEquals(listOf(io.github.wkoller.glucosetracker.ai.AssistantReply.Crisis), replies[3])
    }

    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant()

    @Test
    fun `progress reads each day of the chart`() {
        seedPlan()
        val today = LocalDate.now()
        runBlocking {
            val planId = repository.currentPlan()!!.id
            fun reading(date: LocalDate, slot: Slot, hour: Int, tenths: Int, flag: MeterFlag? = null) =
                GlucoseReading(0, planId, at(date, hour), date, slot, tenths, flag, null)
            repository.addReading(reading(today.minusDays(2), Slot.BREAKFAST, 7, 68))
            repository.addReading(reading(today.minusDays(2), Slot.DINNER, 17, 124))
            repository.addReading(reading(today.minusDays(1), Slot.LUNCH, 12, 11, MeterFlag.LO))
            repository.addReading(reading(today, Slot.BREAKFAST, 7, 72))
            repository.addMeasurement(BodyMeasurement(0, at(today.minusDays(2), 7), today.minusDays(2), 112_400, null, null))
            repository.addMeasurement(BodyMeasurement(0, at(today, 7), today, 111_900, null, null))
        }
        show { ProgressScreen(onBack = {}, onAsk = {}) }
        waitFor("Average by time of day")

        // TalkBack reads the summary, then one stop for each day of glucose readings.
        fun glucoseDay(date: LocalDate) = hasContentDescription(UiFormat.dayLong(date) + ". ", substring = true) and hasContentDescription("mmol/L", substring = true)
        listOf(today.minusDays(2), today.minusDays(1), today).forEach { date ->
            assertEquals(1, compose.onAllNodes(glucoseDay(date)).fetchSemanticsNodes().size)
        }
        compose.onNode(glucoseDay(today.minusDays(2)) and hasContentDescription("12.4 mmol/L before dinner, above your target range.", substring = true)).assertExists()
        compose.onNode(glucoseDay(today.minusDays(1)) and hasContentDescription("LO (below 1.1 mmol/L) before lunch, low.", substring = true)).assertExists()
        compose.onNode(hasContentDescription(UiFormat.dayLong(today) + ". 111.9 kg.")).assertExists()
        val rows = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.CollectionInfo].rowCount }
        assertEquals(listOf(2, 3), rows.sorted())

        // Each fact is one stop, with its label and value together.
        compose.onNode(hasText("Average") and hasText("mmol/L", substring = true)).assertExists()

        // The assistant is not set up in tests, so there is no button to ask it.
        assertTrue(compose.onAllNodesWithText("Ask about these records").fetchSemanticsNodes().isEmpty())
        shot("progress_01_chart")
    }

    @Test
    fun `progress offers to ask the assistant when it is ready`() {
        val today = LocalDate.now()
        val plan = TestPlans.example()
        val readings = (0L..13L).flatMap { back ->
            val date = today.minusDays(back)
            listOf(
                GlucoseReading(0, 1, at(date, 7), date, Slot.BREAKFAST, if (back % 6 == 0L) 36 else 92 + back.toInt() * 3, null, null),
                GlucoseReading(0, 1, at(date, 17), date, Slot.DINNER, 118 + back.toInt() % 4 * 9, null, null),
            )
        }
        val model = ProgressModelBuilder.build(plan, today.minusDays(13), today, readings, emptyList(), emptyList())
        var asked: Int? = null
        show { ProgressContent(ProgressUi(loading = false, days = 14, model = model, canAsk = true), onBack = {}, onAsk = { asked = it }, onDays = {}) }
        shot("progress_02_ask")
        click("Ask about these records")
        assertEquals(14, asked)
    }

    @Test
    fun `the assistant opens on the progress period`() {
        seedPlan()
        val context = runBlocking { app.container.assistant.context(progressDays = 30) }!!
        assertEquals(context.today.minusDays(29), context.progress.from)
        assertEquals(30, context.progress.days)

        // The suggested questions about trends go to the model, not to the fixed dose or urgent answers.
        val names = listOf(context.plan.correctionInsulin) + context.plan.medicines.map { it.name }
        PERIOD_SUGGESTIONS.forEach { res -> assertEquals(app.getString(res), QuestionKind.GENERAL, AssistantGuard.classify(app.getString(res), names)) }

        show { AssistantScreen(onBack = {}, onSetUp = {}, onCheckIn = {}, progressDays = 30) }
        waitFor("The last 30 days")
        waitFor("The assistant can see the same figures as your Progress screen")
        shot("assistant_03_progress")
    }

    @Test
    fun `the calendar colours each day by what was recorded`() {
        val today = LocalDate.now()
        runBlocking {
            val rows = TestPlans.example().toEntities(1, java.time.Instant.now().minus(java.time.Duration.ofDays(20)))
            val planId = app.container.database.planDao().insertFull(rows.plan, rows.bands, rows.slots, rows.medicines)
            app.container.settingsStore.update { it.copy(setupDone = true) }
            fun reading(date: LocalDate, slot: Slot) = GlucoseReading(0, planId, date.atTime(8, 0).atZone(java.time.ZoneId.systemDefault()).toInstant(), date, slot, 80, null, null)
            fun dose(date: LocalDate, slot: Slot, key: String, name: String) = io.github.wkoller.glucosetracker.domain.model.DoseRecord(
                0, planId, null, date.atTime(8, 0).atZone(java.time.ZoneId.systemDefault()).toInstant(), date, slot, DoseKind.FIXED, key, name,
                io.github.wkoller.glucosetracker.domain.model.MedicineKind.TABLET, 1.0, 1.0, "tablet", DoseStatus.GIVEN, null, null,
            )
            val complete = today.minusDays(3)
            Slot.scheduled.forEach { repository.addReading(reading(complete, it)) }
            repository.addDose(dose(complete, Slot.BREAKFAST, "premix", "NovoMix 30"))
            repository.addDose(dose(complete, Slot.DINNER, "premix", "NovoMix 30"))
            repository.addDose(dose(complete, Slot.BREAKFAST, "spiro", "Spironolactone 25 mg"))
            repository.addDose(dose(complete, Slot.BREAKFAST, "furo", "Furosemide 40 mg"))
            repository.addReading(reading(today.minusDays(2), Slot.BREAKFAST))
        }
        show { io.github.wkoller.glucosetracker.ui.logbook.LogbookScreen(onBack = {}, onDay = {}) }
        waitFor("What the colours mean")
        if (today.minusDays(3).month != today.month) click("Earlier month")
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("all recorded", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("partly recorded", substring = true)).onFirst().assertExists()
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("nothing recorded", substring = true)).onFirst().assertExists()
        shot("logbook_01_calendar")
    }

    @Test
    fun `home greets the user by name`() {
        seedPlan()
        runBlocking { app.container.settingsStore.update { it.copy(firstName = "Mary") } }
        show { HomeScreen(HomeActions({ _, _ -> }, {}, {}, {}, {}, {}, {}, {})) }
        waitFor(", Mary")
        waitFor("Open your calendar")
        shot("home_03_name")
    }

    @Test
    fun `check times can be named after the time of day`() {
        seedPlan()
        val settings = Settings(setupDone = true, slotNaming = SlotNaming.TIMES_OF_DAY)
        show(settings) { HomeScreen(HomeActions({ _, _ -> }, {}, {}, {}, {}, {}, {}, {})) }
        waitFor("Morning, 7:30")
        waitFor("Night, 9:00")
        assertTrue(compose.onAllNodesWithText("Breakfast", substring = true).fetchSemanticsNodes().isEmpty())
        shot("home_05_times_of_day")
    }

    @Test
    fun `a check-in uses the chosen names for the check times`() {
        seedPlan()
        show(Settings(setupDone = true, slotNaming = SlotNaming.TIMES_OF_DAY)) { CheckInScreen(onClose = {}, onMeasure = {}, onCheckIn = {}) }
        waitFor("Please enter the reading")
        chooseSlot("Evening")
        waitFor("Your blood glucose level in the evening")
        type("13.7")
        click("Next")
        waitFor("Your dose in the evening")
    }

    @Test
    fun `small text size`() {
        seedPlan()
        show(Settings(setupDone = true, textSize = io.github.wkoller.glucosetracker.data.prefs.TextSize.SMALL)) {
            HomeScreen(HomeActions({ _, _ -> }, {}, {}, {}, {}, {}, {}, {}))
        }
        waitFor("What to do next")
        shot("home_04_small_text")
    }

    @Test
    fun `the model download is declared and starts without crashing`() {
        val info = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions!!.contains(android.Manifest.permission.INTERNET))
        val models = app.container.models
        val directory = app.getExternalFilesDir(io.github.wkoller.glucosetracker.ai.ModelManager.MODEL_DIR)!!.apply { mkdirs() }
        org.robolectric.shadows.ShadowStatFs.registerStats(directory, 2_000_000, 2_000_000, 2_000_000)
        models.startDownload()
        // The assistant's state follows the download a moment later.
        waitForBackgroundWork { models.state.value is io.github.wkoller.glucosetracker.ai.ModelState.Downloading }
        models.cancelDownload()
    }

    @Test
    fun `downloading on mobile data warns about charges first`() {
        val connectivity = app.getSystemService(android.net.ConnectivityManager::class.java)
        val capabilities = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
        org.robolectric.Shadows.shadowOf(capabilities).addTransportType(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
        org.robolectric.Shadows.shadowOf(capabilities).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        org.robolectric.Shadows.shadowOf(connectivity).setNetworkCapabilities(connectivity.activeNetwork, capabilities)
        assertEquals(io.github.wkoller.glucosetracker.download.Connection.MOBILE, io.github.wkoller.glucosetracker.download.NetworkStatus.current(app))
        show { io.github.wkoller.glucosetracker.ui.settings.VoiceScreen(onBack = {}) }
        click("Download natural voices")
        waitFor("You are not connected to Wi-Fi")
        waitFor("may incur charges")
        shot("voice_02_mobile_data")
    }

    @Test
    fun `choose a voice offers the phone voice and the natural voices`() {
        show { io.github.wkoller.glucosetracker.ui.settings.VoiceScreen(onBack = {}) }
        waitFor("The phone\u2019s default voice")
        waitFor("Download natural voices")
        shot("voice_01_choose")
    }

    @Test
    fun `backup and restore offers the phone backup, a backup file and restore`() {
        show { io.github.wkoller.glucosetracker.ui.backup.BackupScreen(onBack = {}) }
        waitFor("Google backup")
        waitFor("You have not saved a backup file yet.")
        waitFor("Restore from a backup file")
        shot("backup_01")
    }

    @Test
    fun `home asks to allow notifications when they are off`() {
        seedPlan()
        org.robolectric.Shadows.shadowOf(app.getSystemService(android.app.NotificationManager::class.java)).setNotificationsEnabled(false)
        show { HomeScreen(HomeActions({ _, _ -> }, {}, {}, {}, {}, {}, {}, {})) }
        waitFor("The app cannot show your reminders")
        compose.onNodeWithText("Allow notifications").assertExists()
        shot("home_07_notifications_off")
    }

    @Test
    fun `a reading that is back up after a low suggests longer-acting carbohydrate`() {
        seedPlan()
        show { CheckInScreen(onClose = {}, onMeasure = {}, onCheckIn = {}) }
        waitFor("Please enter the reading")
        chooseSlot("Lunch")
        type("3.6")
        click("Next")
        click("Enter my new reading")
        waitFor("Please enter the reading")
        type("6.2")
        click("Next")
        waitFor("Your level is back up")
        shot("checkin_33_low_recovered")
    }

    @Test
    fun `the licences list the software and open its text`() {
        val opened = androidx.compose.runtime.mutableStateOf<String?>(null)
        show {
            val file = opened.value
            if (file == null) {
                io.github.wkoller.glucosetracker.ui.settings.LicencesScreen(onBack = {}, onOpen = { opened.value = it })
            } else {
                io.github.wkoller.glucosetracker.ui.settings.LicenceTextScreen(file = file, onBack = {})
            }
        }
        waitFor("LiteRT-LM, which runs the assistant")
        shot("about_01_licences")
        click("The software that LiteRT-LM uses")
        assertEquals("litertlm-android-THIRD_PARTY_NOTICE.txt", opened.value)
        // The notices run to thousands of paragraphs, read from the assets the build collected.
        waitFor("Abseil")
        shot("about_02_licence_text")
    }

    @Test
    fun `a date is set with Earlier and Later buttons`() {
        var date: LocalDate? = LocalDate.of(2026, 10, 8)
        show { io.github.wkoller.glucosetracker.ui.components.DateField("Start date", date, { date = it }) }
        click("Change the date")
        waitFor("Thursday 8 October 2026")
        click("Later")
        waitFor("Friday 9 October 2026")
        shot("setup_10_date_stepper")
        click("Save")
        assertEquals(LocalDate.of(2026, 10, 9), date)
    }

    @Test
    fun `home shows the next step and today`() {
        seedPlan()
        runBlocking {
            val plan = repository.currentPlan()!!
            val today = repository.logicalToday(plan)
            repository.addReading(GlucoseReading(0, plan.id, repository.instant(), today, Slot.BREAKFAST, 137, null, null))
            repository.addMeasurement(io.github.wkoller.glucosetracker.domain.model.BodyMeasurement(0, repository.instant(), LocalDate.now(), 116_510, null, null))
        }
        show { HomeScreen(HomeActions({ _, _ -> }, {}, {}, {}, {}, {}, {}, {})) }
        waitFor("What to do next")
        shot("home_01")
    }

    @Test
    fun `home in maximum contrast and largest text`() {
        seedPlan()
        show(Settings(setupDone = true, maxContrast = true, textSize = io.github.wkoller.glucosetracker.data.prefs.TextSize.LARGEST)) {
            HomeScreen(HomeActions({ _, _ -> }, {}, {}, {}, {}, {}, {}, {}))
        }
        waitFor("What to do next")
        shot("home_02_max_contrast_largest")
    }

    private fun showApp(): androidx.navigation.NavHostController {
        lateinit var nav: androidx.navigation.NavHostController
        show {
            nav = androidx.navigation.compose.rememberNavController()
            io.github.wkoller.glucosetracker.ui.nav.AppNavHost(
                startWithSetup = false, openSlot = null, onSlotOpened = {}, openBackup = false, onBackupOpened = {}, navController = nav,
            )
        }
        compose.waitForIdle()
        return nav
    }

    private fun openDay(nav: androidx.navigation.NavHostController, date: LocalDate) {
        compose.runOnUiThread { nav.navigate(io.github.wkoller.glucosetracker.ui.nav.Routes.day(date.toString())) }
        compose.waitForIdle()
    }

    @Test
    fun `a forgotten reading can be added to an earlier day`() {
        seedPlan()
        val yesterday = runBlocking { repository.logicalToday(repository.currentPlan()) }.minusDays(1)
        val nav = showApp()
        openDay(nav, yesterday)
        waitFor("There are no records for this day.")
        click("Add a record you forgot")
        waitFor("Add a record")
        shot("editor_01_add")
        click("A blood glucose reading")
        click("Lunch")
        click("Next")
        type("8.4")
        click("Next")
        waitFor("What time was it?")
        compose.onNodeWithTag("time_value").assertTextContains("12:00", substring = true)
        compose.onNode(androidx.compose.ui.test.hasContentDescription("5 minutes later")).performClick()
        shot("editor_02_time")
        click("Done")
        waitFor("Check the new record")
        shot("editor_03_summary")
        click("Save")
        waitForBackgroundWork { runBlocking { repository.readingsBetween(yesterday, yesterday) }.isNotEmpty() }
        waitFor("Add a record you forgot")
        waitFor("8.4 mmol/L")

        val saved = runBlocking { repository.readingsBetween(yesterday, yesterday) }.single()
        assertEquals(Slot.LUNCH, saved.slot)
        assertEquals(84, saved.tenths)
        assertEquals(java.time.LocalTime.of(12, 5), saved.takenAt.atZone(java.time.ZoneId.systemDefault()).toLocalTime())
    }

    @Test
    fun `a reading can be changed and a deleted record put back`() {
        seedPlan()
        val today = runBlocking { repository.logicalToday(repository.currentPlan()) }
        runBlocking {
            val plan = repository.currentPlan()!!
            val at = today.atTime(7, 40).atZone(java.time.ZoneId.systemDefault()).toInstant()
            repository.addReading(GlucoseReading(0, plan.id, at, today, Slot.BREAKFAST, 137, null, null))
        }
        val nav = showApp()
        openDay(nav, today)
        waitFor("13.7 mmol/L")
        click("Change")
        waitFor("Change this record")
        click("Change")
        repeat(4) { compose.onNodeWithTag("key_back").performClick() }
        type("12.1")
        click("Done")
        waitFor("12.1 mmol/L")
        click("Save")
        waitFor("Delete")
        waitForBackgroundWork { runBlocking { repository.readingsBetween(today, today) }.single().tenths == 121 }

        click("Delete")
        compose.onNode(hasText("Delete") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isDialog())).performClick()
        waitFor("The record has been deleted.")
        waitForBackgroundWork { runBlocking { repository.readingsBetween(today, today) }.isEmpty() }
        shot("day_01_undo")
        click("Undo")
        waitForBackgroundWork { runBlocking { repository.readingsBetween(today, today) }.size == 1 }
    }

    @Test
    fun `a forgotten dose that differs from the scale asks for confirmation`() {
        seedPlan()
        val yesterday = runBlocking { repository.logicalToday(repository.currentPlan()) }.minusDays(1)
        runBlocking {
            val plan = repository.currentPlan()!!
            val at = yesterday.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()
            repository.addReading(GlucoseReading(0, plan.id, at, yesterday, Slot.LUNCH, 137, null, null))
        }
        val nav = showApp()
        openDay(nav, yesterday)
        waitFor("13.7 mmol/L")
        click("Add a record you forgot")
        click("A medicine or insulin dose")
        click("NovoRapid")
        click("Next")
        click("Lunch")
        click("Next")
        click("Yes, it was given")
        click("Next")
        type("6")
        click("Next")
        click("Done")
        waitFor("Check the new record")
        click("Save")
        waitFor("Your prescription says 4 units for a reading of 13.7 mmol/L. You have entered 6 units.")
        shot("editor_04_dose_confirm")
        click("Record 6 units")
        waitForBackgroundWork { runBlocking { repository.dosesBetween(yesterday, yesterday) }.isNotEmpty() }
        val dose = runBlocking { repository.dosesBetween(yesterday, yesterday) }.single()
        assertEquals(DoseKind.CORRECTION, dose.kind)
        assertEquals(4.0, dose.prescribedAmount!!, 0.0)
        assertEquals(6.0, dose.givenAmount!!, 0.0)
        assertTrue(dose.readingId != null)
    }

    @Test
    fun `the low-reading screen can call 000 or the support person`() {
        seedPlan()
        runBlocking { app.container.settingsStore.update { it.copy(supportName = "Anne", supportPhone = "0400 111 222") } }
        show { CheckInScreen(onClose = {}, onMeasure = {}, onCheckIn = {}) }
        waitFor("Please enter the reading")
        chooseSlot("Lunch")
        type("3.2")
        click("Next")
        waitFor("Call Anne")
        shot("checkin_31_low_call")
        val shadowApp = org.robolectric.Shadows.shadowOf(app)
        click("Call 000")
        val emergency = shadowApp.nextStartedActivity
        assertEquals(android.content.Intent.ACTION_DIAL, emergency.action)
        assertEquals("tel:000", emergency.data.toString())
        click("Call Anne")
        val support = shadowApp.nextStartedActivity
        assertEquals(android.content.Intent.ACTION_DIAL, support.action)
        assertEquals("0400 111 222", support.data!!.schemeSpecificPart)
    }

    @Test
    fun `home asks the user to tell the doctor about repeated lows`() {
        seedPlan()
        val today = runBlocking { repository.logicalToday(repository.currentPlan()) }
        runBlocking {
            val plan = repository.currentPlan()!!
            listOf(today.minusDays(1), today.minusDays(3)).forEach { date ->
                val at = date.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()
                repository.addReading(GlucoseReading(0, plan.id, at, date, Slot.LUNCH, 34, null, null))
            }
        }
        show { HomeScreen(HomeActions({ _, _ -> }, {}, {}, {}, {}, {}, {}, {})) }
        waitFor("low on 2 occasions in the last 7 days")
        shot("home_06_repeated_lows")
        click("I understand")
        waitForBackgroundWork { runBlocking { app.container.settingsStore.current() }.lowNoticeAckAt > 0 }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Repeated low readings").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `a check-in saves what was had for a low and a note`() {
        seedPlan()
        show { CheckInScreen(onClose = {}, onMeasure = {}, onCheckIn = {}) }
        waitFor("Please enter the reading")
        chooseSlot("Lunch")
        type("3.4")
        click("Next")
        waitFor("What did you have?")
        click("Fruit juice")
        shot("checkin_32_low_treatment")
        click("Continue without a new reading")
        waitFor("All done")
        click("Add a note")
        waitFor("Your note")
        compose.onNode(hasSetTextAction()).performTextInput("Skipped morning tea.")
        click("Save")
        waitFor("Skipped morning tea.")
        shot("checkin_06_done_note")

        val today = runBlocking { repository.logicalToday(repository.currentPlan()) }
        waitForBackgroundWork {
            runBlocking { repository.readingsBetween(today, today) }.singleOrNull()?.note == "Had fruit juice for this low reading. Skipped morning tea."
        }
    }
}

package io.github.wkoller.glucosetracker

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.glucosetracker.data.db.toEntities
import io.github.wkoller.glucosetracker.data.prefs.Settings
import io.github.wkoller.glucosetracker.domain.model.DoseKind
import io.github.wkoller.glucosetracker.domain.model.DoseStatus
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.Slot
import io.github.wkoller.glucosetracker.tts.LocalSpeaker
import io.github.wkoller.glucosetracker.ui.checkin.CheckInScreen
import io.github.wkoller.glucosetracker.ui.home.HomeActions
import io.github.wkoller.glucosetracker.ui.home.HomeScreen
import io.github.wkoller.glucosetracker.ui.plan.PlanSetupScreen
import io.github.wkoller.glucosetracker.ui.theme.TrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

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

    private fun show(settings: Settings = Settings(setupDone = true), content: @Composable () -> Unit) {
        compose.setContent {
            TrackerTheme(settings) {
                CompositionLocalProvider(LocalSpeaker provides null) { Surface { content() } }
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

    private fun chooseSlot(slotName: String) {
        click("Change the time of day")
        click(slotName)
    }

    @Test
    fun `first-run setup creates a plan`() {
        var finished = false
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
    fun `scale table errors name the range`() {
        show(Settings()) { PlanSetupScreen(onClose = {}, onFinished = {}) }
        click("Next")
        click("Next")
        waitFor("Your prescription")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("NovoRapid")
        click("Next")
        click("Start with an empty table")
        waitFor("Above the last range")
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("10")
        fields[1].performTextInput("0")
        fields[2].performTextInput("8")
        click("Next")
        waitFor("Please fill in every box in the table.")
        // Fill the empty boxes so the out-of-order number is the only problem left.
        val boxes = compose.onAllNodes(hasSetTextAction())
        boxes[3].performTextInput("2")
        listOf(4 to "14", 5 to "4", 6 to "17", 7 to "6", 8 to "20", 9 to "8", 10 to "10").forEach { (i, v) -> boxes[i].performTextInput(v) }
        click("Next")
        waitFor("In the row that starts at 10.1 mmol/L, the top of the range must be more than the top of the range in the row above.")
        shot("setup_09_scale_errors")
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
    fun `dose questions are answered by the app, not the model`() {
        seedPlan()
        val service = app.container.assistant
        val replies = runBlocking {
            val context = service.context()!!
            listOf("How many units of NovoRapid should I take?", "I have chest pain").map { question ->
                val collected = mutableListOf<io.github.wkoller.glucosetracker.ai.AssistantReply>()
                service.ask(context, question, emptyList()).collect { collected += it }
                collected
            }
        }
        assertEquals(listOf(io.github.wkoller.glucosetracker.ai.AssistantReply.DoseQuestion), replies[0])
        assertEquals(listOf(io.github.wkoller.glucosetracker.ai.AssistantReply.Urgent), replies[1])
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
        waitFor("Tap to see your calendar.")
        shot("home_03_name")
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
        assertTrue(models.state.value is io.github.wkoller.glucosetracker.ai.ModelState.Downloading)
        models.cancelDownload()
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
}

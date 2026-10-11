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

    private fun show(settings: Settings = Settings(setupDone = true), content: @Composable () -> Unit) {
        compose.setContent {
            TrackerTheme(settings) {
                CompositionLocalProvider(LocalSpeaker provides null) { Surface { content() } }
            }
        }
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
        show(Settings()) { PlanSetupScreen(onClose = {}, onFinished = {}) }
        waitFor("Welcome")
        shot("setup_01_welcome")
        click("Next")
        waitFor("Your prescription")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("NovoRapid")
        shot("setup_02_prescription")
        click("Next")
        click("Fill in a common scale")
        shot("setup_03_scale")
        click("Next")
        click("I have checked this against my doctor’s order.")
        shot("setup_04_scale_check")
        click("Next")
        waitFor("Check times")
        shot("setup_05_times")
        click("Next")
        click("Add a medicine")
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("NovoMix 30")
        click("Insulin, measured in units")
        compose.onAllNodes(hasSetTextAction())[2].performTextInput("30")
        compose.onAllNodes(hasSetTextAction())[4].performTextInput("26")
        shot("setup_06_medicine_editor")
        click("Save this medicine")
        waitFor("NovoMix 30")
        shot("setup_07_medicines")
        click("Next")
        waitFor("Your goals")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("115")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("100")
        click("Next")
        waitFor("Your target range")
        shot("setup_08_targets")
        click("Next")
        click("Continue without a PIN")
        waitFor("Allow notifications")
        click("Next")
        waitFor("Please review your plan")
        shot("setup_09_review")
        click("Start my plan")
        compose.waitUntil(10_000) { runBlocking { repository.currentPlan() } != null }
        val plan = runBlocking { repository.currentPlan()!! }
        assertEquals("NovoRapid", plan.correctionInsulin)
        assertEquals(6, plan.scale.bands.size)
        assertEquals(mapOf(Slot.BREAKFAST to 30.0, Slot.DINNER to 26.0), plan.medicines.single().doses)
        assertEquals(115_000, plan.goals.startWeightGrams)
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
    fun `home shows the next step and today`() {
        seedPlan()
        runBlocking {
            val plan = repository.currentPlan()!!
            val today = repository.logicalToday(plan)
            repository.addReading(GlucoseReading(0, plan.id, repository.instant(), today, Slot.BREAKFAST, 137, null, null))
            repository.addMeasurement(io.github.wkoller.glucosetracker.domain.model.BodyMeasurement(0, repository.instant(), LocalDate.now(), 116_510, null, null))
        }
        show { HomeScreen(HomeActions({ _, _ -> }, {}, {}, {}, {}, {}, {})) }
        waitFor("What to do next")
        shot("home_01")
    }

    @Test
    fun `home in maximum contrast and largest text`() {
        seedPlan()
        show(Settings(setupDone = true, maxContrast = true, textSize = io.github.wkoller.glucosetracker.data.prefs.TextSize.LARGEST)) {
            HomeScreen(HomeActions({ _, _ -> }, {}, {}, {}, {}, {}, {}))
        }
        waitFor("What to do next")
        shot("home_02_max_contrast_largest")
    }
}

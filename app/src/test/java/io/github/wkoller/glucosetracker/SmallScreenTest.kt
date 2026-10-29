package io.github.wkoller.glucosetracker

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.glucosetracker.data.prefs.PinHasher
import io.github.wkoller.glucosetracker.data.prefs.Settings
import io.github.wkoller.glucosetracker.data.prefs.TextSize
import io.github.wkoller.glucosetracker.ui.UiFormat
import io.github.wkoller.glucosetracker.ui.checkin.CheckInScreen
import io.github.wkoller.glucosetracker.ui.logbook.LogbookScreen
import io.github.wkoller.glucosetracker.ui.measure.MeasureScreen
import io.github.wkoller.glucosetracker.ui.plan.PlanSetupScreen
import io.github.wkoller.glucosetracker.ui.plan.UnlockScreen
import io.github.wkoller.glucosetracker.ui.theme.TrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Number entry on a small phone. The keypad and the button under it must be on screen without
 * scrolling, at the default text size, at the largest text size with the phone's own font enlarged,
 * and with the phone turned sideways. Pictures are saved to build/screenshots as small_*.png.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class SmallScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: TrackerApp get() = ApplicationProvider.getApplicationContext()

    private val largest = Settings(setupDone = true, textSize = TextSize.LARGEST)

    @Before
    fun seed() {
        runBlocking {
            app.container.settingsStore.update { Settings(setupDone = true) }
            app.container.repository.savePlanVersion(TestPlans.example())
        }
    }

    @After
    fun resetFontScale() {
        RuntimeEnvironment.setFontScale(1f)
    }

    private fun show(settings: Settings, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { TrackerTheme(settings) { Surface { content() } } }
    }

    /** The phone's own font setting at its larger steps, on top of the app's largest text size. */
    private fun enlargePhoneFont() = RuntimeEnvironment.setFontScale(1.3f)

    private fun waitFor(text: String) = compose.waitUntil(timeoutMillis = 10_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun click(text: String) {
        waitFor(text)
        val node = compose.onAllNodesWithText(text).onFirst()
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    private fun assertKeypadOnScreen(button: String) {
        listOf("1", "3", "7", "9", "0", "back").forEach {
            compose.onNodeWithTag("key_$it").assertIsDisplayed().assertHeightIsAtLeast(MIN_KEY)
        }
        compose.onNodeWithText(button).assertIsDisplayed()
    }

    /** LO, HI and the time of day sit beside the keypad and beside Back, so none of them is hidden under the keypad. */
    private fun assertGlucoseChoicesOnScreen() {
        compose.onNodeWithTag("key_lo").assertIsDisplayed().assertHeightIsAtLeast(MIN_KEY)
        compose.onNodeWithTag("key_hi").assertIsDisplayed().assertHeightIsAtLeast(MIN_KEY)
        compose.onNodeWithTag("time_of_day").assertIsDisplayed()
    }

    private fun type(value: String) = value.forEach { compose.onNodeWithTag("key_$it").performClick() }

    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `glucose entry keeps the keypad on screen`() {
        show(Settings(setupDone = true)) { CheckInScreen(onClose = {}, onMeasure = {}, onCheckIn = {}) }
        waitFor("Please enter the reading")
        assertKeypadOnScreen("Next")
        type("13.7")
        assertGlucoseChoicesOnScreen()
        shot("small_01_bgl")
    }

    @Test
    fun `glucose entry at the largest text keeps the keypad on screen`() {
        enlargePhoneFont()
        show(largest) { CheckInScreen(onClose = {}, onMeasure = {}, onCheckIn = {}) }
        waitFor("Please enter the reading")
        assertKeypadOnScreen("Next")
        type("13.7")
        assertGlucoseChoicesOnScreen()
        shot("small_02_bgl_largest")
    }

    @Test
    fun `an error shows next to the keypad`() {
        enlargePhoneFont()
        show(largest) { CheckInScreen(onClose = {}, onMeasure = {}, onCheckIn = {}) }
        waitFor("Please enter the reading")
        type("40")
        click("Next")
        compose.onNodeWithText("Please enter a reading of 33.3 or less.").assertIsDisplayed()
        assertKeypadOnScreen("Next")
        assertGlucoseChoicesOnScreen()
        shot("small_03_bgl_error")
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun `glucose entry on a phone turned sideways puts the keypad beside the heading`() {
        show(largest) { CheckInScreen(onClose = {}, onMeasure = {}, onCheckIn = {}) }
        waitFor("Please enter the reading")
        assertKeypadOnScreen("Next")
        type("13.7")
        assertGlucoseChoicesOnScreen()
        shot("small_04_bgl_landscape")
    }

    @Test
    fun `the dose result keeps its main button on screen at the largest text`() {
        enlargePhoneFont()
        show(largest) { CheckInScreen(onClose = {}, onMeasure = {}, onCheckIn = {}) }
        waitFor("Please enter the reading")
        compose.onNodeWithTag("time_of_day").performClick()
        click("Lunch")
        type("13.7")
        click("Next")
        waitFor("Your prescription says to give 4 units of NovoRapid.")
        compose.onNodeWithText("I gave 4 units").assertIsDisplayed()
        shot("small_05_result_largest")
        // The other answers are still there, at the end of the page.
        click("I gave a different amount")
        waitFor("How much NovoRapid did you give?")
        assertKeypadOnScreen("Next")
        shot("small_06_amount_largest")
    }

    @Test
    fun `weight entry keeps the keypad on screen at the largest text`() {
        enlargePhoneFont()
        show(largest) { MeasureScreen(onClose = {}, onCheckIn = {}) }
        waitFor("Please weigh yourself")
        assertKeypadOnScreen("Save")
        type("116.5")
        shot("small_07_weight_largest")
    }

    @Test
    fun `the PIN keypad stays on screen at the largest text`() {
        val salt = PinHasher.newSalt()
        runBlocking { app.container.settingsStore.update { it.copy(pinSalt = salt, pinHash = PinHasher.hash("1234", salt)) } }
        enlargePhoneFont()
        show(largest) { UnlockScreen(onBack = {}, onUnlocked = {}) }
        waitFor("Please enter your PIN")
        compose.onNodeWithTag("key_1").assertIsDisplayed().assertHeightIsAtLeast(MIN_KEY)
        compose.onNodeWithTag("key_back").assertIsDisplayed().assertHeightIsAtLeast(MIN_KEY)
        compose.onNodeWithText("Unlock", substring = true).assertIsDisplayed()
        shot("small_08_pin_largest")
    }

    @Test
    fun `a PIN that starts with 0 unlocks`() {
        val salt = PinHasher.newSalt()
        runBlocking { app.container.settingsStore.update { it.copy(pinSalt = salt, pinHash = PinHasher.hash("0412", salt)) } }
        var unlocked = false
        show(Settings(setupDone = true)) { UnlockScreen(onBack = {}, onUnlocked = { unlocked = true }) }
        waitFor("Please enter your PIN")
        type("0412")
        click("Unlock")
        assertTrue("The PIN 0412 should unlock the plan", unlocked)
    }

    @Test
    fun `the sliding-scale keypad stays on screen at the largest text`() {
        enlargePhoneFont()
        show(largest.copy(setupDone = false)) { PlanSetupScreen(onClose = {}, onFinished = {}) }
        click("Next")
        click("Next")
        waitFor("Your prescription")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("NovoRapid")
        click("Next")
        click("Start with an empty table")
        waitFor("Row 1 of 6")
        assertKeypadOnScreen("Next")
        type("10")
        shot("small_09_scale_largest")
    }

    @Test
    fun `the logbook list keeps the chart and the day in view on a small phone`() {
        val today = runBlocking { app.container.repository.logicalToday(app.container.repository.currentPlan()) }
        runBlocking { TestRecords.seedLogbook(app.container.repository, today) }
        show(Settings(setupDone = true)) { LogbookScreen(onBack = {}, onDay = {}) }
        click("List")
        waitFor("Today, " + UiFormat.dayLong(today))
        shot("small_10_logbook_list")

        // Scrolling slides Back and the heading away, while the chart and the day's heading stay at the top.
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp(startY = bottom * 0.9f, endY = top, durationMillis = 400) }
        compose.waitForIdle()
        compose.onNodeWithText("Back").assertIsNotDisplayed()
        compose.onNode(hasContentDescription(UiFormat.dayLong(today) + ". ", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("Today, " + UiFormat.dayLong(today)).assertIsDisplayed()
        shot("small_11_logbook_scrolled")
    }

    @Test
    fun `the logbook list at the largest text`() {
        enlargePhoneFont()
        val today = runBlocking { app.container.repository.logicalToday(app.container.repository.currentPlan()) }
        runBlocking { TestRecords.seedLogbook(app.container.repository, today) }
        show(largest) { LogbookScreen(onBack = {}, onDay = {}) }
        click("List")
        waitFor("Today, " + UiFormat.dayLong(today))
        compose.onNode(hasContentDescription("Breakfast. 13.7 mmol/L", substring = true)).assertExists()
        shot("small_12_logbook_largest")
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun `the full-screen chart keeps its buttons and values on screen at the largest text`() {
        enlargePhoneFont()
        val repository = app.container.repository
        val today = runBlocking { repository.logicalToday(repository.currentPlan()) }
        runBlocking {
            TestRecords.seedLogbook(repository, today)
            TestRecords.seedHistory(repository, today)
        }
        lateinit var nav: androidx.navigation.NavHostController
        show(largest) {
            nav = androidx.navigation.compose.rememberNavController()
            io.github.wkoller.glucosetracker.ui.nav.AppNavHost(
                startWithSetup = false, openSlot = null, onSlotOpened = {}, openBackup = false, onBackupOpened = {}, navController = nav,
            )
        }
        compose.runOnUiThread { nav.navigate(io.github.wkoller.glucosetracker.ui.nav.Routes.logbookChart(today.minusDays(1).toString())) }
        waitFor("Earlier day")
        listOf("Back", "Earlier day", "Later day").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNode(hasContentDescription("Dinner. 15.2 mmol/L", substring = true)).assertIsDisplayed()
        shot("small_13_day_chart_largest")
    }

    private companion object {
        /** Keys may shrink a little to make room for an error, but never below a comfortable touch target. */
        val MIN_KEY = 48.dp
    }
}

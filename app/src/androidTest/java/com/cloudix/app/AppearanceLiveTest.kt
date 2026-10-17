package com.cloudix.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the original bug: saved appearance changes must be visible immediately,
 * without restarting the Activity or recreating CloudixTheme. */
@RunWith(AndroidJUnit4::class)
class AppearanceLiveTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var settings: CloudixSettings

    @Before fun setup() {
        settings = CloudixSettings(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        settings.resetAppearance()
        settings.theme = "light"
        settings.dynamic = false
        settings.animations = false
    }

    @Test fun newAccentRecomposesImmediately() {
        composeRule.setContent {
            CloudixTheme(settings) {
                Text(
                    text = MaterialTheme.colorScheme.primary.toArgb().toString(),
                    modifier = Modifier.testTag("live-color")
                )
            }
        }
        val first = hexColor(CloudixAppearanceCatalog.presets[0].hex).toArgb().toString()
        val mustard = hexColor(CloudixAppearanceCatalog.presets[11].hex).toArgb().toString()
        composeRule.onNodeWithTag("live-color").assertTextEquals(first)
        composeRule.runOnUiThread { settings.accent = 11 }
        composeRule.onNodeWithTag("live-color").assertTextEquals(mustard)
    }

    @Test fun amoledChangesBackgroundWithoutRestart() {
        composeRule.setContent {
            CloudixTheme(settings) {
                Text(
                    text = MaterialTheme.colorScheme.background.toArgb().toString(),
                    modifier = Modifier.testTag("live-background")
                )
            }
        }
        val original = cloudixScheme(androidx.compose.material3.lightColorScheme(),
            settings.appearance, false).background.toArgb().toString()
        composeRule.onNodeWithTag("live-background").assertTextEquals(original)
        composeRule.runOnUiThread { settings.surfaceStyle = "amoled_plain" }
        composeRule.onNodeWithTag("live-background").assertTextEquals(
            androidx.compose.ui.graphics.Color.Black.toArgb().toString()
        )
    }
}

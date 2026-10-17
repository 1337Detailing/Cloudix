package com.cloudix.app

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regression tests for settings previously stored only in SharedPreferences. */
@RunWith(AndroidJUnit4::class)
class SettingsLiveTest {
    @get:Rule val rule = createComposeRule()

    @Test fun sortAndBackupSettingsUpdateWithoutActivityRestart() {
        val settings = CloudixSettings(InstrumentationRegistry.getInstrumentation().targetContext)
        settings.sort = "name"
        settings.wifiOnly = true
        rule.setContent {
            Text(
                "${settings.sort};${settings.wifiOnly}",
                modifier = Modifier.testTag("live-preferences")
            )
        }
        rule.onNodeWithTag("live-preferences").assertTextEquals("name;true")
        rule.runOnUiThread {
            settings.sort = "date"
            settings.wifiOnly = false
        }
        rule.waitForIdle()
        rule.onNodeWithTag("live-preferences").assertTextEquals("date;false")
    }

    @Test fun automaticStartupUpdatesImmediately() {
        val settings = CloudixSettings(InstrumentationRegistry.getInstrumentation().targetContext)
        settings.serverAutoStart = false
        rule.setContent {
            Text(settings.serverAutoStart.toString(), modifier = Modifier.testTag("startup"))
        }
        rule.onNodeWithTag("startup").assertTextEquals("false")
        rule.runOnUiThread { settings.serverAutoStart = true }
        rule.waitForIdle()
        rule.onNodeWithTag("startup").assertTextEquals("true")
    }
}

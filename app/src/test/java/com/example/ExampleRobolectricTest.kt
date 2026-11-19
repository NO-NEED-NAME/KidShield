package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesStore
import com.example.service.InternetLockVpnService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `setup persists pin across relaunch and vpn service runs cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("KidShield", context.getString(R.string.app_name))

        composeRule.waitForIdle()
        // Step 1: Enter 4-digit PIN: 1234
        composeRule.onNodeWithTag("pin_key_1").performClick()
        composeRule.onNodeWithTag("pin_key_2").performClick()
        composeRule.onNodeWithTag("pin_key_3").performClick()
        composeRule.onNodeWithTag("pin_key_4").performClick()
        composeRule.waitForIdle()

        // Step 2: Confirm 4-digit PIN: 1234
        composeRule.onNodeWithTag("pin_key_1").performClick()
        composeRule.onNodeWithTag("pin_key_2").performClick()
        composeRule.onNodeWithTag("pin_key_3").performClick()
        composeRule.onNodeWithTag("pin_key_4").performClick()
        composeRule.waitForIdle()

        // Step 3: Enter recovery answer and complete setup
        composeRule.onNodeWithTag("setup_recovery_input").performScrollTo().performTextInput("ali")
        composeRule.onNodeWithTag("setup_complete_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Verify Dashboard renders immediately
        composeRule.onNodeWithTag("shield_toggle_orb").assertExists()

        // Verify a brand-new PreferencesStore instance sees setup as persisted
        val freshStore = PreferencesStore(composeRule.activity.applicationContext)
        assertTrue(freshStore.getSnapshot().isSetupComplete)
        assertTrue(freshStore.verifyPin("1234"))
        assertFalse(freshStore.verifyPin("9999"))

        // Verify VPN service starts and stops without crashing
        val intent = Intent(context, InternetLockVpnService::class.java).apply {
            action = InternetLockVpnService.ACTION_ENGAGE_LOCK
        }
        val controller = Robolectric.buildService(InternetLockVpnService::class.java, intent)
        controller.create().startCommand(0, 1)
        controller.destroy()
    }
}

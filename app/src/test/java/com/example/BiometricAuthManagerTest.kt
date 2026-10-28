package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.biometric.BiometricAuthManager
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BiometricAuthManagerTest {

  @Test
  fun `checkBiometricAvailability returns valid status without crash`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = BiometricAuthManager(context)

    val availability = manager.checkBiometricAvailability()
    assertNotNull(availability)
  }
}

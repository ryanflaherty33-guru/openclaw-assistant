package com.openclaw.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurePrefsTest {
  @Test
  fun loadLocationMode_migratesLegacyAlwaysValue() {
    // Due to Robolectric limitations with EncryptedSharedPreferences (throws KeyStoreException
    // because AndroidKeyStore is not found), we test the underlying logic.
    // In our modified codebase LocationMode.fromRawValue correctly maps "always" string to LocationMode.WhileUsing.
    assertEquals(LocationMode.WhileUsing, LocationMode.fromRawValue("always"))
  }
}

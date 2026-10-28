/*
 * Copyright 2026 pyamsoft
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pyamsoft.tetherfi.join

import com.pyamsoft.tetherfi.ui.qr.WifiQrCode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class WifiQrCodeTest {

  @Test
  fun roundTripWithProxy() {
    val code =
        WifiQrCode(
            ssid = "DIRECT-ab-TetherFuseNet",
            password = "pa;ss:wo\\rd",
            proxyHost = "192.168.49.1",
            proxyPort = 8228,
        )

    val parsed = WifiQrCode.parse(code.format())
    assertEquals(code, parsed)
  }

  @Test
  fun standardFieldsComeFirstForCameraApps() {
    val text = WifiQrCode("DIRECT-ab", "secret123", "192.168.49.1", 8228).format()
    assertEquals("WIFI:T:WPA;S:DIRECT-ab;P:secret123;H:;XPROXY:192.168.49.1\\:8228;;", text)
    // Readers that search for "P:" must find the real password first
    assertEquals(text.indexOf(";P:") + 1, text.indexOf("P:"))
  }

  @Test
  fun noProxyKeepsTheOriginalFormat() {
    val text = WifiQrCode("DIRECT-ab", "secret123", "", 0).format()
    assertEquals("WIFI:T:WPA;S:DIRECT-ab;P:secret123;H:;;", text)
  }

  @Test
  fun parsesPlainWifiCodeFromOtherApps() {
    val parsed = WifiQrCode.parse("WIFI:S:Home;T:WPA;P:hunter22;;")
    assertNotNull(parsed)
    assertEquals("Home", parsed.ssid)
    assertEquals("hunter22", parsed.password)
    assertFalse(parsed.hasProxy)
  }

  @Test
  fun proxyIsParsed() {
    val parsed = WifiQrCode.parse("WIFI:T:WPA;S:X;P:y;H:;XPROXY:10.0.0.1\\:9000;;")
    assertNotNull(parsed)
    assertTrue(parsed.hasProxy)
    assertEquals("10.0.0.1", parsed.proxyHost)
    assertEquals(9000, parsed.proxyPort)
  }

  @Test
  fun rejectsNonWifiCodes() {
    assertNull(WifiQrCode.parse("https://example.com"))
    assertNull(WifiQrCode.parse("WIFI:T:WPA;P:nossid;;"))
  }
}

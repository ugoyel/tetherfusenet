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

package com.pyamsoft.tetherfi.ui.qr

import androidx.annotation.CheckResult

/**
 * A standard "WIFI:" QR code, optionally carrying the hotspot's proxy address.
 *
 * The proxy goes in an extra XPROXY field placed after the standard fields. QR readers that do not
 * know the field (phone cameras, Android Settings) skip it and join the network as usual, while
 * TetherFuseNet's own scanner uses it to show the proxy settings to enter.
 */
data class WifiQrCode(
    val ssid: String,
    val password: String,
    val proxyHost: String,
    val proxyPort: Int,
) {

  val hasProxy: Boolean = proxyHost.isNotBlank() && proxyPort > 0

  @CheckResult
  fun format(): String {
    return buildString {
      append("WIFI:T:WPA;S:")
      append(escape(ssid))
      append(";P:")
      append(escape(password))
      append(";H:;")
      if (hasProxy) {
        append(KEY_PROXY)
        append(escape("$proxyHost:$proxyPort"))
        append(";")
      }
      append(";")
    }
  }

  companion object {

    private const val PREFIX = "WIFI:"
    private const val KEY_PROXY = "XPROXY:"
    private const val SPECIAL_CHARS = "\\;,:\""

    @JvmStatic
    @CheckResult
    private fun escape(value: String): String {
      return buildString {
        for (c in value) {
          if (c in SPECIAL_CHARS) {
            append('\\')
          }
          append(c)
        }
      }
    }

    /** Split the body on unescaped ';' and drop the escaping */
    @JvmStatic
    @CheckResult
    private fun splitFields(body: String): List<String> {
      val fields = mutableListOf<String>()
      val current = StringBuilder()
      var escaped = false
      for (c in body) {
        when {
          escaped -> {
            current.append(c)
            escaped = false
          }
          c == '\\' -> escaped = true
          c == ';' -> {
            fields.add(current.toString())
            current.clear()
          }
          else -> current.append(c)
        }
      }
      if (current.isNotEmpty()) {
        fields.add(current.toString())
      }
      return fields
    }

    /** Parse a scanned QR code, or null if it is not a WIFI: code with a network name */
    @JvmStatic
    @CheckResult
    fun parse(raw: String): WifiQrCode? {
      val text = raw.trim()
      if (!text.startsWith(PREFIX, ignoreCase = true)) {
        return null
      }

      var ssid = ""
      var password = ""
      var proxyHost = ""
      var proxyPort = 0
      for (field in splitFields(text.substring(PREFIX.length))) {
        val split = field.indexOf(':')
        if (split <= 0) {
          continue
        }

        val value = field.substring(split + 1)
        when (field.substring(0, split).uppercase()) {
          "S" -> ssid = value
          "P" -> password = value
          KEY_PROXY.dropLast(1) -> {
            val portSplit = value.lastIndexOf(':')
            if (portSplit > 0) {
              proxyHost = value.substring(0, portSplit)
              proxyPort = value.substring(portSplit + 1).toIntOrNull() ?: 0
            }
          }
        }
      }

      if (ssid.isBlank()) {
        return null
      }

      return WifiQrCode(
          ssid = ssid,
          password = password,
          proxyHost = proxyHost,
          proxyPort = proxyPort,
      )
    }
  }
}

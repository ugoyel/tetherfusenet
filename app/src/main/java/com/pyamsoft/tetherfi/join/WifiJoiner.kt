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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import androidx.annotation.CheckResult
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.pyamsoft.pydroid.core.LintIgnoreTooGenericExceptionCaught
import com.pyamsoft.tetherfi.core.Timber
import com.pyamsoft.tetherfi.ui.qr.WifiQrCode

/** Joins this device to a scanned hotspot and helps the user enter its proxy */
internal object WifiJoiner {

  /** Wi-Fi Direct group owners always use this address */
  const val DEFAULT_PROXY_HOST = "192.168.49.1"

  /** Matches ServerDefaults.HTTP_PORT, used if the QR code did not carry a proxy */
  const val DEFAULT_PROXY_PORT = 8228

  private const val CHANNEL_ID = "tetherfi_join_proxy"
  private const val NOTIFICATION_ID = 4242

  /** How the scanned network gets saved, which depends on the Android version */
  enum class Method {
    /** Android 11+: the system "Save this network?" sheet */
    SYSTEM_SHEET,

    /** Android 10: no app can save a normal network, the user picks it in Settings */
    MANUAL,

    /** Android 9 and older: the app saves and connects the network itself */
    DIRECT,
  }

  @JvmStatic
  @CheckResult
  fun method(): Method {
    return when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Method.SYSTEM_SHEET
      Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> Method.MANUAL
      else -> Method.DIRECT
    }
  }

  /** Intent for the Android 11+ system sheet that saves the network as a normal, editable one */
  @JvmStatic
  @CheckResult
  @RequiresApi(Build.VERSION_CODES.R)
  fun createAddNetworkIntent(code: WifiQrCode): Intent {
    val builder = WifiNetworkSuggestion.Builder().setSsid(code.ssid)
    if (code.password.isNotEmpty()) {
      builder.setWpa2Passphrase(code.password)
    }

    return Intent(Settings.ACTION_WIFI_ADD_NETWORKS).apply {
      putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(builder.build()))
    }
  }

  /** Whether the system sheet saved the network (or it was already saved) */
  @JvmStatic
  @CheckResult
  @RequiresApi(Build.VERSION_CODES.R)
  fun isAddNetworkSuccess(resultCode: Int, data: Intent?): Boolean {
    if (resultCode != Activity.RESULT_OK) {
      return false
    }

    val results = data?.getIntegerArrayListExtra(Settings.EXTRA_WIFI_NETWORK_RESULT_LIST)
    // Some OEM sheets return OK without a list, trust the OK
    if (results.isNullOrEmpty()) {
      return true
    }

    return results.all {
      it == Settings.ADD_WIFI_RESULT_SUCCESS || it == Settings.ADD_WIFI_RESULT_ALREADY_EXISTS
    }
  }

  /** Android 9 and older: save and connect the network directly */
  @JvmStatic
  @CheckResult
  @SuppressLint("MissingPermission")
  @Suppress("DEPRECATION")
  fun connectDirectly(context: Context, code: WifiQrCode): Boolean {
    try {
      val wifi = context.applicationContext.getSystemService<WifiManager>() ?: return false
      if (!wifi.isWifiEnabled) {
        wifi.isWifiEnabled = true
      }

      val config =
          WifiConfiguration().apply {
            SSID = "\"${code.ssid}\""
            if (code.password.isEmpty()) {
              allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            } else {
              preSharedKey = "\"${code.password}\""
            }
          }

      val networkId = wifi.addNetwork(config)
      if (networkId == -1) {
        Timber.w { "addNetwork failed for ${code.ssid}" }
        return false
      }

      return wifi.enableNetwork(networkId, true)
    } catch (@LintIgnoreTooGenericExceptionCaught e: Throwable) {
      Timber.e(e) { "Failed to join network ${code.ssid}" }
      return false
    }
  }

  @JvmStatic
  fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService<ClipboardManager>() ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
  }

  @JvmStatic
  fun openWifiSettings(context: Context) {
    try {
      context.startActivity(
          Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    } catch (@LintIgnoreTooGenericExceptionCaught e: Throwable) {
      Timber.e(e) { "Unable to open Wi-Fi settings" }
    }
  }

  /** Keep the proxy values in the notification shade while the user is in Settings */
  @JvmStatic
  @SuppressLint("MissingPermission")
  fun showProxyReminder(context: Context, ssid: String, host: String, port: Int) {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
    ) {
      Timber.w { "No notification permission, skip proxy reminder" }
      return
    }

    context.getSystemService<NotificationManager>()?.also { manager ->
      manager.createNotificationChannel(
          NotificationChannel(
              CHANNEL_ID,
              "Proxy setup help",
              NotificationManager.IMPORTANCE_DEFAULT,
          )
      )
    }

    val text = "Proxy: Manual. Hostname: $host. Port: $port"
    val notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.pyamsoft.tetherfi.service.R.drawable.ic_wifi_tethering_24)
            .setContentTitle("Proxy for $ssid")
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "In Wi-Fi settings, edit \"$ssid\" and open Advanced options.\n$text"
                    )
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
  }
}

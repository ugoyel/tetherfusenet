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

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.pyamsoft.tetherfi.ui.qr.WifiQrCode

/** Where the join flow is, saved across rotation */
private enum class JoinStep {
  READY,
  CONNECTING,
  SET_PROXY,
  FAILED,
}

@Composable
internal fun JoinHotspotScreen(
    modifier: Modifier = Modifier,
    debugScanResult: String?,
    onClose: () -> Unit,
) {
  val context = LocalContext.current

  var step by rememberSaveable { mutableStateOf(JoinStep.READY) }
  var scanned by rememberSaveable { mutableStateOf("") }
  var error by rememberSaveable { mutableStateOf("") }
  var autoScanned by rememberSaveable { mutableStateOf(false) }

  val code = WifiQrCode.parse(scanned)
  val proxyHost = code?.proxyHost?.takeIf { code.hasProxy } ?: WifiJoiner.DEFAULT_PROXY_HOST
  val proxyPort = code?.proxyPort?.takeIf { code.hasProxy } ?: WifiJoiner.DEFAULT_PROXY_PORT

  val addNetworkLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
        ->
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                WifiJoiner.isAddNetworkSuccess(result.resultCode, result.data)
        ) {
          step = JoinStep.SET_PROXY
        } else {
          error = "The Wi-Fi network was not saved. Scan again and tap Save when Android asks."
          step = JoinStep.FAILED
        }
      }

  val handleScanned = { contents: String ->
    val parsed = WifiQrCode.parse(contents)
    if (parsed == null) {
      error = "That QR code is not a Wi-Fi code. Scan the QR code shown by TetherFuseNet."
      step = JoinStep.FAILED
    } else {
      scanned = contents
      when (WifiJoiner.method()) {
        WifiJoiner.Method.SYSTEM_SHEET -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            step = JoinStep.CONNECTING
            addNetworkLauncher.launch(WifiJoiner.createAddNetworkIntent(parsed))
          }
        }
        WifiJoiner.Method.DIRECT -> {
          if (WifiJoiner.connectDirectly(context, parsed)) {
            step = JoinStep.SET_PROXY
          } else {
            error = "Could not join ${parsed.ssid}. Is Wi-Fi turned on?"
            step = JoinStep.FAILED
          }
        }
        WifiJoiner.Method.MANUAL -> {
          WifiJoiner.copyToClipboard(context, "Wi-Fi password", parsed.password)
          step = JoinStep.SET_PROXY
        }
      }
    }
  }

  val scanLauncher =
      rememberLauncherForActivityResult(ScanContract()) { result ->
        // Null contents means the scan was cancelled, stay where we are
        result.contents?.also { handleScanned(it) }
      }

  val launchScan = {
    scanLauncher.launch(
        ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Scan the QR code on the hotspot phone")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
    )
  }

  // Opening this screen goes straight to the scanner, once
  LaunchedEffect(Unit) {
    if (!autoScanned) {
      autoScanned = true
      if (debugScanResult != null) {
        handleScanned(debugScanResult)
      } else {
        launchScan()
      }
    }
  }

  Surface(modifier = modifier.fillMaxSize()) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
          text = "Join a hotspot",
          style = MaterialTheme.typography.headlineMedium,
      )

      Text(
          text =
              "On the hotspot phone, turn on TetherFuseNet and open its QR code. " +
                  "Then scan it here. This phone joins the Wi-Fi with the password, " +
                  "and shows you the proxy settings to enter.",
          style = MaterialTheme.typography.bodyLarge,
      )

      when (step) {
        JoinStep.CONNECTING ->
            Text(
                text = "Tap Save when Android asks to save ${code?.ssid.orEmpty()}.",
                style = MaterialTheme.typography.bodyLarge,
            )
        JoinStep.FAILED ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        else -> Unit
      }

      Spacer(modifier = Modifier.weight(1F))

      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
      ) {
        OutlinedButton(onClick = onClose) { Text(text = "Close") }
        Button(onClick = { launchScan() }) { Text(text = "Scan QR code") }
      }
    }
  }

  if (step == JoinStep.SET_PROXY && code != null) {
    ProxySetupDialog(
        ssid = code.ssid,
        host = proxyHost,
        port = proxyPort,
        isManualJoin = WifiJoiner.method() == WifiJoiner.Method.MANUAL,
        onCopy = { label, value ->
          WifiJoiner.copyToClipboard(context, label, value)
          Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
        },
        onDismiss = { step = JoinStep.READY },
        onConfirm = {
          WifiJoiner.copyToClipboard(context, "Proxy hostname", proxyHost)
          WifiJoiner.showProxyReminder(context, code.ssid, proxyHost, proxyPort)
          WifiJoiner.openWifiSettings(context)
          step = JoinStep.READY
          onClose()
        },
    )
  }
}

@Composable
private fun ProxySetupDialog(
    ssid: String,
    host: String,
    port: Int,
    isManualJoin: Boolean,
    onCopy: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = {
        Text(text = if (isManualJoin) "Join $ssid" else "Wi-Fi saved: $ssid")
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
              text =
                  if (isManualJoin) {
                    "The Wi-Fi password is copied. Click OK to open Wi-Fi settings, tap " +
                        "\"$ssid\", paste the password, and open Advanced options. " +
                        "Set Proxy to Manual and enter:"
                  } else {
                    "Last step: set the proxy for this Wi-Fi. Click OK to open Wi-Fi settings, " +
                        "then edit \"$ssid\" (tap it, then the pencil or Modify), open " +
                        "Advanced options, set Proxy to Manual and enter:"
                  },
          )

          ProxyValueRow(label = "Hostname", value = host, onCopy = onCopy)
          ProxyValueRow(label = "Port", value = port.toString(), onCopy = onCopy)

          Text(
              text = "Then tap Save. These values also stay in your notifications.",
              style = MaterialTheme.typography.bodySmall,
          )
        }
      },
      confirmButton = { TextButton(onClick = onConfirm) { Text(text = "OK") } },
      dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel") } },
  )
}

@Composable
private fun ProxyValueRow(
    label: String,
    value: String,
    onCopy: (String, String) -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1F)) {
      Text(text = label, style = MaterialTheme.typography.labelMedium)
      Text(
          text = value,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
      )
    }
    TextButton(onClick = { onCopy(label, value) }) { Text(text = "Copy") }
  }
}

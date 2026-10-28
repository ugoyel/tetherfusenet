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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyamsoft.pydroid.arch.SaveStateDisposableEffect
import com.pyamsoft.pydroid.core.requireNotNull
import com.pyamsoft.pydroid.util.AppDispatchers
import com.pyamsoft.tetherfi.BuildConfig
import com.pyamsoft.tetherfi.ObjectGraph
import com.pyamsoft.tetherfi.TFTheme
import com.pyamsoft.tetherfi.getSystemDarkMode
import com.pyamsoft.tetherfi.main.SystemBars
import com.pyamsoft.tetherfi.main.ThemeViewModeler
import javax.inject.Inject

/** Joins this device to another phone's hotspot by scanning its Wi-Fi QR code */
class JoinHotspotActivity : ComponentActivity() {

  @Inject @JvmField internal var dispatchers: AppDispatchers? = null
  @Inject @JvmField internal var viewModel: ThemeViewModeler? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    ObjectGraph.ApplicationScope.retrieve(this).plusJoinHotspot().create().inject(this)

    val vm = viewModel.requireNotNull()
    val dis = dispatchers.requireNotNull()

    // Debug builds only: skip the camera with a scan result, used to test on emulators
    val debugScanResult =
        if (BuildConfig.DEBUG) intent.getStringExtra(EXTRA_DEBUG_SCAN_RESULT) else null

    setContent {
      val theme by vm.mode.collectAsStateWithLifecycle()
      val isMaterialYou by vm.isMaterialYou.collectAsStateWithLifecycle()

      SaveStateDisposableEffect(vm)

      TFTheme(
          dispatchers = dis,
          theme = theme,
          isMaterialYou = isMaterialYou,
      ) {
        SystemBars(
            isDarkMode = theme.getSystemDarkMode(),
        )
        JoinHotspotScreen(
            debugScanResult = debugScanResult,
            onClose = { finish() },
        )
      }
    }

    vm.init(this)
  }

  override fun onDestroy() {
    super.onDestroy()
    viewModel = null
    dispatchers = null
  }

  companion object {
    private const val EXTRA_DEBUG_SCAN_RESULT = "debug_scan_result"
  }
}

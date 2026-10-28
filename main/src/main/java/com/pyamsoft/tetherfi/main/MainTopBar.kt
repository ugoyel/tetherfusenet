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

package com.pyamsoft.tetherfi.main

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun MainTopBar(
    modifier: Modifier = Modifier,
    appName: String,
    actions: @Composable RowScope.() -> Unit = {},
) {
  Surface(
      modifier = modifier,
      contentColor = MaterialTheme.colorScheme.onPrimary,
      color = MaterialTheme.colorScheme.primary,
      shape =
          MaterialTheme.shapes.large.copy(
              topStart = ZeroCornerSize,
              topEnd = ZeroCornerSize,
          ),
  ) {
    MainToolbar(
        modifier = Modifier.statusBarsPadding(),
        appName = appName,
        actions = actions,
    )
  }
}

@Preview
@Composable
private fun PreviewMainTopBar() {
  MainTopBar(
      appName = "TEST",
  )
}

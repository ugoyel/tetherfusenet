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

package com.pyamsoft.tetherfi.widget

import androidx.annotation.CheckResult
import com.pyamsoft.tetherfi.service.tile.TileHandler
import dagger.Subcomponent

@Subcomponent
internal interface ProxyWidgetComponent {

  fun inject(provider: ProxyWidgetProvider)

  /** The same status source the Quick Settings tile uses */
  @CheckResult fun tileHandler(): TileHandler

  @Subcomponent.Factory
  interface Factory {

    @CheckResult fun create(): ProxyWidgetComponent
  }
}

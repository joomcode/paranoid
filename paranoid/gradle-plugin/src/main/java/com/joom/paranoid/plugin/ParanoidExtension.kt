/*
 * Copyright 2021 SIA Joom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.joom.paranoid.plugin

import com.joom.paranoid.processor.logging.getLogger
import java.io.File
import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty

open class ParanoidExtension {
  @Deprecated(IS_ENABLED_DEPRECATION_WARNING)
  var isEnabled: Boolean by deprecatedProperty(true, IS_ENABLED_DEPRECATION_WARNING)

  @Deprecated(IS_CACHEABLE_DEPRECATION_WARNING)
  var isCacheable: Boolean by deprecatedProperty(false, IS_CACHEABLE_DEPRECATION_WARNING)

  @Deprecated(INCLUDE_SUBPROJECT_DEPRECATION_WARNING)
  var includeSubprojects by deprecatedProperty(false, INCLUDE_SUBPROJECT_DEPRECATION_WARNING)

  var obfuscationSeed: Int? = null
  var bootClasspath: List<File> = emptyList()
  var applyToBuildTypes: BuildType = BuildType.ALL

  private inline fun <reified T : Any> deprecatedProperty(initial: T, message: String): ReadWriteProperty<Any?, T> {
    return Delegates.observable(initial) { _, _, _ ->
      getLogger().warn("WARNING: $message")
    }
  }
}

enum class BuildType {
  NONE,
  ALL,
  NOT_DEBUGGABLE
}

private const val IS_ENABLED_DEPRECATION_WARNING = "paranoid.enabled is deprecated. Use paranoid.applyToBuildTypes"
private const val IS_CACHEABLE_DEPRECATION_WARNING = "paranoid.isCacheable is deprecated"
private const val INCLUDE_SUBPROJECT_DEPRECATION_WARNING = "paranoid.includeSubprojects is deprecated"

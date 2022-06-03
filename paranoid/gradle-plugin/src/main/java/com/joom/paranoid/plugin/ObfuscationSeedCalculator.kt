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

import java.io.File

object ObfuscationSeedCalculator {

  fun <T : Any> calculate(inputs: List<T>, fileSelector: (T) -> File): Int {
    return inputs.maxLastModified { getLastModified(fileSelector(it)) }.hashCode()
  }

  private fun getLastModified(file: File): Long {
    return file.walk().asIterable().maxLastModified { it.lastModified() }
  }

  private fun <T : Any> Iterable<T>.maxLastModified(lastModified: (T) -> Long): Long {
    var result = 0L
    forEach {
      result = maxOf(result, lastModified(it))
    }
    return result
  }
}

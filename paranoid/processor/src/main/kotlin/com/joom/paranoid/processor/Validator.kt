/*
 * Copyright 2022 SIA Joom
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

package com.joom.paranoid.processor

import com.joom.grip.Grip
import com.joom.grip.classes
import com.joom.paranoid.processor.watermark.WatermarkChecker
import java.io.File

class Validator(private val grip: Grip, private val asmApi: Int) {

  fun validate(inputs: Collection<File>) {
    val errors = ArrayList<String>()
    val registry = newObfuscatedTypeRegistry(grip.classRegistry).withCache()
    val query = grip select classes from inputs where registry.shouldObfuscate()
    query.execute().types.forEach {
      val file = grip.fileRegistry.findFileForType(it) ?: run {
        errors += "File not found for class ${it.className}"
        return@forEach
      }

      if (!WatermarkChecker.isParanoidClass(file, asmApi)) {
        errors += "Class ${it.className} is not processed by paranoid, is paranoid plugin applied to module?"
      }
    }

    if (errors.isNotEmpty()) {
      throw ParanoidException(errors.joinToString(separator = "\n"))
    }
  }
}


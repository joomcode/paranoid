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

package com.joom.paranoid.processor.watermark

import org.objectweb.asm.Attribute
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import java.io.File
import java.io.IOException

class WatermarkChecker(asmApi: Int) : ClassVisitor(asmApi) {
  var isParanoidClass: Boolean = false
    private set

  override fun visit(
    version: Int,
    access: Int,
    name: String,
    signature: String?,
    superName: String?,
    interfaces: Array<String>?
  ) {
    isParanoidClass = false
  }

  override fun visitAttribute(attr: Attribute) {
    if (attr is ParanoidAttribute) {
      isParanoidClass = true
    }
  }

  companion object {
    private const val CLASS_EXTENSION = "class"

    @Throws(IOException::class)
    @JvmStatic
    fun isParanoidClass(file: File, asmApi: Int): Boolean {
      if (!file.extension.equals(CLASS_EXTENSION, true)) {
        return false
      }

      val classReader = ClassReader(file.readBytes())
      val checker = WatermarkChecker(asmApi)
      classReader.accept(
        checker,
        arrayOf<Attribute>(ParanoidAttribute()),
        ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
      )
      return checker.isParanoidClass
    }
  }
}

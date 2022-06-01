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
import org.objectweb.asm.ClassVisitor

open class WatermarkClassVisitor(
  asmApi: Int,
  classVisitor: ClassVisitor,
  private val isDirtyByDefault: Boolean = false,
) : ClassVisitor(asmApi, classVisitor) {

  private var isDirty: Boolean = false
  private var isAttributeAdded: Boolean = false

  override fun visit(
    version: Int,
    access: Int,
    name: String,
    signature: String?,
    superName: String?,
    interfaces: Array<out String>?
  ) {
    isDirty = isDirtyByDefault
    isAttributeAdded = false
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitAttribute(attr: Attribute) {
    if (attr is ParanoidAttribute) {
      if (!isAttributeAdded) {
        super.visitAttribute(attr)
      }
      isAttributeAdded = true
    } else {
      super.visitAttribute(attr)
    }
  }

  override fun visitEnd() {
    if (!isAttributeAdded && isDirty) {
      visitAttribute(ParanoidAttribute())
    }
    super.visitEnd()
  }
}

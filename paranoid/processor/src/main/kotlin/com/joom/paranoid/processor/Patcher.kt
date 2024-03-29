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

package com.joom.paranoid.processor

import com.joom.grip.ClassRegistry
import com.joom.grip.io.FileSink
import com.joom.grip.io.FileSource
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.getObjectTypeByInternalName
import com.joom.paranoid.processor.logging.getLogger
import com.joom.paranoid.processor.model.Deobfuscator
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

class Patcher(
  private val deobfuscator: Deobfuscator,
  private val stringRegistry: StringRegistry,
  private val analysisResult: AnalysisResult,
  private val classRegistry: ClassRegistry,
  private val asmApi: Int,
) {

  private val logger = getLogger()

  fun copyAndPatchClasses(sources: List<FileSource>, sink: FileSink) {
    sources.forEach {
      copyAndPatchClasses(it, sink)
      sink.flush()
    }
  }

  private fun copyAndPatchClasses(source: FileSource, sink: FileSink) {
    logger.info("Patching...")
    logger.info("   Input: {}", source)
    logger.info("  Output: {}", sink)

    source.listFiles { name, type ->
      when (type) {
        FileSource.EntryType.CLASS -> copyAndPatchClass(source, sink, name)
        FileSource.EntryType.FILE -> source.copyFileTo(sink, name)
        FileSource.EntryType.DIRECTORY -> sink.createDirectory(name)
      }
    }
  }

  private fun copyAndPatchClass(source: FileSource, sink: FileSink, name: String) {
    if (!maybePatchClass(source, sink, name)) {
      source.copyFileTo(sink, name)
    }
  }

  private fun getObjectTypeFromFile(relativePath: String): Type.Object? {
    if (relativePath.endsWith(".class")) {
      val internalName = relativePath.substringBeforeLast(".class").replace('\\', '/')
      return getObjectTypeByInternalName(internalName)
    }
    return null
  }

  private fun maybePatchClass(source: FileSource, sink: FileSink, name: String): Boolean {
    val type = getObjectTypeFromFile(name) ?: run {
      logger.error("Skip patching for {}", name)
      return false
    }

    val configuration = analysisResult.configurationsByType[type]
    val hasObfuscateAnnotation = OBFUSCATE_TYPE in classRegistry.getClassMirror(type).annotations
    if (configuration == null && !hasObfuscateAnnotation) {
      return false
    }

    logger.debug("Patching class {}", name)
    val reader = ClassReader(source.readFile(name))
    val writer = StandaloneClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES, classRegistry)
    val shouldObfuscateLiterals = reader.access and Opcodes.ACC_INTERFACE == 0
    val patcher =
      writer
        .wrapIf(hasObfuscateAnnotation) { RemoveObfuscateClassPatcher(asmApi, it) }
        .wrapIf(configuration != null) { StringLiteralsClassPatcher(deobfuscator, stringRegistry, asmApi, it) }
        .wrapIf(configuration != null && shouldObfuscateLiterals) { StringConstantsClassPatcher(configuration!!, asmApi, it) }
    reader.accept(patcher, ClassReader.SKIP_FRAMES)
    sink.createFile(name, writer.toByteArray())
    return true
  }

  private inline fun ClassVisitor.wrapIf(condition: Boolean, wrapper: (ClassVisitor) -> ClassVisitor): ClassVisitor {
    return if (condition) wrapper(this) else this
  }

  private fun FileSource.copyFileTo(sink: FileSink, name: String) {
    sink.createFile(name, readFile(name))
  }
}

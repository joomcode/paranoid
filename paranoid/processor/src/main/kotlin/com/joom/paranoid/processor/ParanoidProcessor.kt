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

import com.joom.grip.Grip
import com.joom.grip.GripFactory
import com.joom.grip.io.IoFactory
import com.joom.grip.mirrors.getObjectTypeByInternalName
import com.joom.paranoid.processor.commons.closeQuietly
import com.joom.paranoid.processor.logging.getLogger
import com.joom.paranoid.processor.model.Deobfuscator
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import java.io.File
import java.nio.file.Paths

class ParanoidProcessor(
  private val obfuscationSeed: Int,
  private val inputs: List<File>,
  private val output: File,
  private val classpath: Collection<File>,
  private val validationClasspath: Collection<File>,
  private val bootClasspath: Collection<File>,
  private val projectName: String,
  private val validateClasspath: Boolean,
  private val asmApi: Int = Opcodes.ASM9,
) {

  private val logger = getLogger()

  private val grip: Grip = GripFactory.newInstance(asmApi).create(inputs + classpath + bootClasspath + validationClasspath)
  private val stringRegistry = StringRegistryImpl(obfuscationSeed)
  private val validator: Validator = Validator(grip, asmApi)

  fun process() {
    dumpConfiguration()

    if (validateClasspath) {
      validator.validate(validationClasspath)
    }

    val analysisResult = Analyzer(grip).analyze(inputs)
    analysisResult.dump()

    val deobfuscator = createDeobfuscator()
    logger.info("Prepare to generate {}", deobfuscator)

    val sources = inputs.map {
      IoFactory.createFileSource(it)
    }

    val sink = createFileSink(output)

    try {
      Patcher(deobfuscator, stringRegistry, analysisResult, grip.classRegistry, asmApi).copyAndPatchClasses(sources, sink)
      val deobfuscatorBytes = DeobfuscatorGenerator(deobfuscator, stringRegistry, grip.classRegistry)
        .generateDeobfuscator()
      val deobfuscatorPath = "${deobfuscator.type.internalName}.class"

      sink.createFile(deobfuscatorPath, deobfuscatorBytes)
      sink.flush()
    } finally {
      sources.forEach {
        it.closeQuietly()
      }
      sink.closeQuietly()
    }
  }

  private fun dumpConfiguration() {
    logger.info("Starting ParanoidProcessor:")
    logger.info("  inputs        = {}", inputs)
    logger.info("  output        = {}", output)
    logger.info("  classpath     = {}", classpath)
    logger.info("  bootClasspath = {}", bootClasspath)
    logger.info("  projectName   = {}", projectName)
  }

  private fun AnalysisResult.dump() {
    if (configurationsByType.isEmpty()) {
      logger.info("No classes to obfuscate")
    } else {
      logger.info("Classes to obfuscate:")
      configurationsByType.forEach {
        val (type, configuration) = it
        logger.info("  {}:", type.internalName)
        configuration.constantStringsByFieldName.forEach {
          val (field, string) = it
          logger.info("    {} = \"{}\"", field, string)
        }
      }
    }
  }

  private fun createDeobfuscator(): Deobfuscator {
    val deobfuscatorInternalName = "com/joom/paranoid/Deobfuscator${composeDeobfuscatorNameSuffix()}"
    val deobfuscatorType = getObjectTypeByInternalName(deobfuscatorInternalName)
    val deobfuscationMethod = Method("getString", Type.getType(String::class.java), arrayOf(Type.LONG_TYPE))
    return Deobfuscator(deobfuscatorType, deobfuscationMethod)
  }

  private fun composeDeobfuscatorNameSuffix(): String {
    val normalizedProjectName = projectName.filter { it.isLetterOrDigit() || it == '_' || it == '$' }
    return if (normalizedProjectName.isEmpty() || normalizedProjectName.startsWith('$')) {
      normalizedProjectName
    } else {
      "$$normalizedProjectName"
    }
  }
}

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

package com.joom.paranoid.plugin

import com.joom.paranoid.processor.ParanoidProcessor
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class ParanoidTransformTask : DefaultTask() {
  @get:InputFiles
  @get:Classpath
  abstract val inputClasses: ListProperty<Directory>

  @get:InputFiles
  @get:Classpath
  abstract val classpath: ConfigurableFileCollection

  @get:InputFiles
  @get:Classpath
  abstract val validationClasspath: ConfigurableFileCollection

  @get:InputFiles
  @get:Classpath
  abstract val bootClasspath: ConfigurableFileCollection

  @get:OutputDirectory
  @get:Optional
  abstract val output: DirectoryProperty

  @get:OutputDirectories
  @get:Optional
  abstract val outputDirectories: ListProperty<File>

  @Input
  @Optional
  var obfuscationSeed: Int? = null

  @Input
  var validateClasspath: Boolean = false

  private val projectName = computeProjectName()

  init {
    logging.captureStandardOutput(LogLevel.INFO)
  }

  @TaskAction
  fun process() {
    try {
      validate()

      cleanOutput()

      val outputs = when {
        output.isPresent -> List(inputClasses.get().size) { output.get().asFile }
        outputDirectories.isPresent -> outputDirectories.get()
        else -> error("output or outputDirectories is not set")
      }

      val genPath = when {
        output.isPresent -> output.get().asFile
        outputDirectories.isPresent -> outputDirectories.get().first()
        else -> error("output or outputDirectories is not set")
      }

      val processor = ParanoidProcessor(
        obfuscationSeed = calculateObfuscationSeed(),
        inputs = inputClasses.get().map { it.asFile },
        outputs = outputs,
        genPath = genPath,
        classpath = classpath.files,
        bootClasspath = bootClasspath.files,
        validationClasspath = validationClasspath.files,
        validateClasspath = validateClasspath,
        projectName = projectName,
      )

      processor.process()
    } catch (exception: Exception) {
      throw GradleScriptException("Failed to transform with paranoid", exception)
    }
  }

  private fun cleanOutput() {
    if (output.isPresent) {
      output.get().asFile.deleteRecursively()
    }

    if (outputDirectories.isPresent) {
      outputDirectories.get().forEach { it.deleteRecursively() }
    }
  }

  private fun calculateObfuscationSeed(): Int {
    obfuscationSeed?.let {
      return it
    }

    return ObfuscationSeedCalculator.calculate(inputClasses.get()) { it.asFile }
  }

  private fun validate() {
    require(inputClasses.get().isNotEmpty()) { "inputClasses is not set" }
    require(output.isPresent || outputDirectories.isPresent) { "output is not set" }
  }

  private fun computeProjectName(): String {
    return (project.path + name.replace(TASK_PREFIX, ":")).replace(':', '$')
  }

  companion object {
    const val TASK_PREFIX = "paranoidTransformClasses"
  }
}

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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class BackupClassesTask : DefaultTask() {

  @InputFiles
  var classesDirs: List<File> = emptyList()

  @OutputDirectories
  var backupDirs: List<File> = emptyList()

  @TaskAction
  fun process() {
    validate()

    classesDirs.zip(backupDirs) { classesDir, backupDir ->
      if (!classesDir.exists()) {
        backupDir.deleteRecursively()
      } else {
        copyFiles(classesDir, backupDir)
      }
    }
  }

  private fun copyFiles(classesDir: File, backupDir: File) {
    backupDir.deleteRecursively()
    classesDir.copyRecursively(backupDir, overwrite = true)
  }

  private fun validate() {
    require(classesDirs.isNotEmpty()) { "classesDirs is not set" }
    require(backupDirs.isNotEmpty()) { "backupDirs is not set" }
    require(classesDirs.size == backupDirs.size) { "classesDirs and backupDirs must have equal size" }
  }
}

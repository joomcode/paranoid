/*
 * Copyright 2020 SIA Joom
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

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

class ParanoidPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    if (!project.hasAndroid) {
      throw GradleException("Paranoid plugin must be applied *AFTER* Android plugin")
    }

    val extension = project.extensions.create("paranoid", ParanoidExtension::class.java)
    val transform = ParanoidTransform(extension)

    project.addDependencies(getDefaultConfiguration())
    project.android.registerTransform(transform)

    project.afterEvaluate {
      extension.bootClasspath = project.android.bootClasspath
    }
  }

  private fun getDefaultConfiguration(): String {
    return JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME
  }

  private fun Project.addDependencies(configurationName: String) {
    dependencies.add(configurationName, "com.joom.paranoid:paranoid-core:${Build.VERSION}")
  }
}

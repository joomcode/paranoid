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

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.variant.Variant
import com.android.build.gradle.internal.scope.getDirectories
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

class ParanoidPlugin : Plugin<Project> {
  private lateinit var project: Project
  override fun apply(project: Project) {
    this.project = project

    val extension = project.extensions.create("paranoid", ParanoidExtension::class.java)
    project.addDependencies(getDefaultConfiguration())

    if (!project.hasAndroid && project.hasJava) {
      registerParanoidForJava(extension)
      return
    }

    if (!project.hasAndroid) {
      throw GradleException("Paranoid plugin must be applied *AFTER* Android plugin")
    }

    val androidComponentsExtension = project.androidComponents
    if (androidComponentsExtension != null && androidComponentsExtension.pluginVersion >= VARIANT_API_REQUIRED_VERSION) {
      project.logger.info("Registering paranoid with variant API")

      registerParanoidWithVariantApi(extension)
    } else {
      project.logger.info("Registering paranoid with transform API")

      registerParanoidWithTransform(extension)
    }
  }

  private fun registerParanoidForJava(extension: ParanoidExtension) {
    if (extension.applyToBuildTypes == BuildType.NONE) {
      return
    }

    val mainSourceSet = project.sourceSets.main
    val compileTask = project.getTaskByName<JavaCompile>(mainSourceSet.compileJavaTaskName)
    val taskProvider = project.registerTask<ParanoidTransformTask>(formatParanoidTaskName(project.name))
    val input = mainSourceSet.output.classesDirs.getDirectories(project.layout.projectDirectory)
    val output = project.layout.buildDirectory.dir("intermediates/paranoid/classes")
    val runtimeClasspath = project.configurations.named(mainSourceSet.runtimeClasspathConfigurationName)
    taskProvider.configure { task ->
      val javaCompileTask = compileTask.get()
      task.obfuscationSeed = extension.obfuscationSeed
      task.bootClasspath.setFrom(javaCompileTask.options.bootstrapClasspath?.files.orEmpty())
      task.classpath.setFrom(javaCompileTask.classpath)
      task.validationClasspath.setFrom(runtimeClasspath.map { it.incomingJarArtifacts { it is ProjectComponentIdentifier }.artifactFiles })
      task.inputClasses.set(input)
      task.output.set(output)
    }

    project.getTaskByName<Jar>(JavaPlugin.JAR_TASK_NAME).configure { jar ->
      val inputDirectories = lazy { input.get().map { it.asFile } }
      jar.dependsOn(taskProvider)
      jar.from(output)
      jar.exclude { element ->
        inputDirectories.value.any { element.file.startsWith(it) }
      }
    }
  }

  private fun registerParanoidWithVariantApi(extension: ParanoidExtension) {
    if (extension.applyToBuildTypes == BuildType.NONE) {
      return
    }

    project.applicationAndroidComponents?.apply {
      onVariants { variant ->
        if (extension.applyToBuildTypes == BuildType.NOT_DEBUGGABLE && variant.isDebuggable()) {
          return@onVariants
        }

        variant.registerParanoidTransformTask(extension, validateClasspath = true)
      }
    }

    project.libraryAndroidComponents?.apply {
      onVariants { variant ->
        if (extension.applyToBuildTypes == BuildType.NOT_DEBUGGABLE && variant.isDebuggable()) {
          return@onVariants
        }

        variant.registerParanoidTransformTask(extension, validateClasspath = false)
      }
    }
  }

  private fun Variant.isDebuggable(): Boolean {
    return buildType?.let { project.android.buildTypes.getByName(it) }?.isDebuggable ?: false
  }

  private fun Variant.registerParanoidTransformTask(
    extension: ParanoidExtension,
    validateClasspath: Boolean,
  ) {
    val taskProvider = project.registerTask<ParanoidTransformTask>(formatParanoidTaskName(name))
    @Suppress("UnstableApiUsage")
    artifacts.use(taskProvider)
      .wiredWith(ParanoidTransformTask::inputClasses, ParanoidTransformTask::output)
      .toTransform(MultipleArtifact.ALL_CLASSES_DIRS)

    val runtimeClasspath = project.configurations.getByName("${name}RuntimeClasspath")

    taskProvider.configure { task ->
      task.obfuscationSeed = extension.obfuscationSeed
      task.validateClasspath = validateClasspath
      task.validationClasspath.setFrom(
        runtimeClasspath.incomingJarArtifacts { it is ProjectComponentIdentifier }.artifactFiles
      )
      task.classpath.setFrom(
        runtimeClasspath.incomingJarArtifacts().artifactFiles
      )

      task.bootClasspath.setFrom(project.android.bootClasspath)
    }
  }

  private fun formatParanoidTaskName(variantName: String): String {
    return "${ParanoidTransformTask.TASK_PREFIX}${variantName.replaceFirstChar { it.uppercase() }}"
  }

  @Suppress("DEPRECATION")
  private fun registerParanoidWithTransform(extension: ParanoidExtension) {
    val transform = ParanoidTransform(extension)
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

  private companion object {
    private val VARIANT_API_REQUIRED_VERSION = AndroidPluginVersion(major = 7, minor = 2, micro = 0)
  }
}

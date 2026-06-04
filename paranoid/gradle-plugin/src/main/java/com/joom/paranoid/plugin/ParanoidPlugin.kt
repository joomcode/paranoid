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
import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.Artifact.Category
import com.android.build.api.artifact.Artifact.Transformable
import com.android.build.api.artifact.ArtifactKind
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.Directory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File

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

    val androidComponents = project.androidComponents
      ?: throw GradleException(
        "Paranoid plugin requires Android Gradle Plugin $MIN_AGP_VERSION or newer " +
          "(androidComponents extension is missing)"
      )

    if (androidComponents.pluginVersion < MIN_AGP_VERSION) {
      throw GradleException(
        "Paranoid plugin requires Android Gradle Plugin $MIN_AGP_VERSION or newer, " +
          "but ${androidComponents.pluginVersion} is used"
      )
    }

    configureVariants(
      components = project.applicationAndroidComponents,
      extension = extension,
      validateClasspath = true,
    )

    configureVariants(
      components = project.libraryAndroidComponents,
      extension = extension,
      validateClasspath = false,
    )
  }

  private fun configureVariants(
    components: AndroidComponentsExtension<*, *, *>?,
    extension: ParanoidExtension,
    validateClasspath: Boolean,
  ) {
    components?.onVariants(components.selector().all()) { variant ->
      if (extension.applyToBuildTypes.isVariantFit(variant)) {
        variant.registerParanoidTransformTask(extension, validateClasspath)
      }
    }
  }

  private fun registerParanoidForJava(extension: ParanoidExtension) {
    val mainSourceSet = project.sourceSets.main
    val classesTask = project.tasks.named(mainSourceSet.classesTaskName)
    val compileTask = project.getTaskByName<JavaCompile>(mainSourceSet.compileJavaTaskName)
    val paranoidTask = project.registerTask<ParanoidTransformTask>(formatParanoidTaskName(project.name))
    val backupClassesTask = project.registerTask<BackupClassesTask>(formatBackupClassesTaskName(project.name))
    val input = mainSourceSet.output.classesDirs.files
    val output = project.layout.buildDirectory.dir("intermediates/paranoid/classes")
    val backupDirs = computeBackupDirs(project.layout.buildDirectory.get().asFile, output.get().asFile, input)
    val runtimeClasspath = project.configurations.named(mainSourceSet.runtimeClasspathConfigurationName)

    backupClassesTask.configure { task ->
      task.classesDirs = input.toList()
      task.backupDirs = backupDirs.toList()
    }

    paranoidTask.configure { task ->
      val javaCompileTask = compileTask.get()
      task.obfuscationSeed = extension.obfuscationSeed
      task.bootClasspath.setFrom(javaCompileTask.options.bootstrapClasspath?.files.orEmpty())
      task.classpath.setFrom(javaCompileTask.classpath)
      task.validationClasspath.setFrom(runtimeClasspath.map { it.incomingJarArtifacts { it is ProjectComponentIdentifier }.artifactFiles })
      task.inputClasses.set(backupDirs.map { file -> project.layout.dir(project.provider { file }).get() })
      task.outputDirectories.set(input)
      task.onlyIf { extension.applyToBuildTypes != BuildType.NONE }

      task.mustRunAfter(compileTask)
      task.dependsOn(compileTask)
      task.dependsOn(backupClassesTask)
    }

    backupClassesTask.configure { task ->
      task.onlyIf { extension.applyToBuildTypes != BuildType.NONE }
      task.dependsOn(compileTask)
    }

    classesTask.configure { task ->
      task.dependsOn(paranoidTask)
    }
  }

  private fun BuildType.isVariantFit(variant: Variant): Boolean {
    return when (this) {
      BuildType.NONE -> false
      BuildType.ALL -> true
      BuildType.NOT_DEBUGGABLE -> !variant.isDebuggable()
    }
  }

  private fun Variant.isDebuggable(): Boolean {
    return debuggable
  }

  private fun Variant.registerParanoidTransformTask(
    extension: ParanoidExtension,
    validateClasspath: Boolean,
  ) {
    val taskProvider = project.registerTask<ParanoidTransformTask>(formatParanoidTaskName(name))

    artifacts.use(taskProvider)
      .wiredWith(ParanoidTransformTask::inputClasses, ParanoidTransformTask::output)
      .toTransform(ArtifactAllClasses)

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

      @Suppress("UnstableApiUsage")
      task.bootClasspath.from(project.androidComponents!!.sdkComponents.bootClasspath)
    }
  }

  private fun formatParanoidTaskName(variantName: String): String {
    return "${ParanoidTransformTask.TASK_PREFIX}${variantName.replaceFirstChar { it.uppercase() }}"
  }

  private fun formatBackupClassesTaskName(variantName: String): String {
    return "paranoidBackupClassesTask${variantName.replaceFirstChar { it.uppercase() }}"
  }

  private fun computeBackupDirs(buildDir: File, paranoidDir: File, classesDirs: Collection<File>): Collection<File> {
    return classesDirs.map { classesDir ->
      val relativeFile = classesDir.relativeToOrSelf(buildDir)
      File(paranoidDir, relativeFile.path)
    }
  }

  private fun getDefaultConfiguration(): String {
    return JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME
  }

  private fun Project.addDependencies(configurationName: String) {
    dependencies.add(configurationName, "com.joom.paranoid:paranoid-core:${Build.VERSION}")
  }

  private object ArtifactAllClasses : Artifact.Multiple<Directory>(
    kind = ArtifactKind.DIRECTORY,
    category = Category.INTERMEDIATES
  ), Transformable

  private companion object {
    private val MIN_AGP_VERSION = AndroidPluginVersion(major = 7, minor = 4, micro = 0)
  }
}

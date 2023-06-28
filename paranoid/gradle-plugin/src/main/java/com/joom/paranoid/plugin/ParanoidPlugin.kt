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
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.TaskProvider
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

    val androidComponentsExtension = project.androidComponents ?: throw GradleException("Failed to get androidComponents extension")
    if (androidComponentsExtension.pluginVersion < MINIMUM_VERSION) {
      throw GradleException("Paranoid requires Android Gradle Plugin version $MINIMUM_VERSION")
    }

    registerParanoid(extension)
  }

  private fun registerParanoidForJava(extension: ParanoidExtension) {
    val mainSourceSet = project.sourceSets.main
    val classesTask = project.tasks.named(mainSourceSet.classesTaskName)
    val compileTask = project.getTaskByName<JavaCompile>(mainSourceSet.compileJavaTaskName)
    val paranoidTask = project.registerTask<ParanoidTransformTask>(formatParanoidTaskName(project.name))
    val backupClassesTask = project.registerTask<BackupClassesTask>(formatBackupClassesTaskName(project.name))
    val input = mainSourceSet.output.classesDirs.files
    val output = project.layout.buildDirectory.dir("intermediates/paranoid/classes")
    val backupDirs = computeBackupDirs(project.buildDir, output.get().asFile, input)
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
      task.inputDirectories.set(backupDirs.map { file -> project.layout.dir(project.provider { file }).get() })
      task.outputDirectory.set(input.single())
      task.onlyIf { extension.applyToBuildTypes != BuildType.NONE }

      task.mustRunAfter(compileTask)
      task.dependsOn(compileTask)
    }

    backupClassesTask.configure { task ->
      task.onlyIf { extension.applyToBuildTypes != BuildType.NONE }
    }

    backupClassesTask.configure { it.dependsOn(compileTask) }
    paranoidTask.configure { it.dependsOn(backupClassesTask) }
    classesTask.configure { it.dependsOn(paranoidTask) }
  }

  private fun registerParanoid(extension: ParanoidExtension) {
    project.applicationAndroidComponents?.apply {
      onVariants(selector().all()) { variant ->
        if (extension.applyToBuildTypes.isVariantFit(variant)) {
          variant.createParanoidTransformTask(extension, validateClasspath = true)
        }
      }
    }

    project.libraryAndroidComponents?.apply {
      onVariants(selector().all()) { variant ->
        if (extension.applyToBuildTypes.isVariantFit(variant)) {
          variant.createParanoidTransformTask(extension, validateClasspath = false)
        }
      }
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
    return buildType?.let { project.android.buildTypes.getByName(it) }?.isDebuggable ?: false
  }

  private fun Variant.createParanoidTransformTask(
    extension: ParanoidExtension,
    validateClasspath: Boolean,
  ) {
    val taskProvider = project.registerTask<ParanoidTransformTask>(formatParanoidTaskName(name))
    val androidComponentsExtension = project.androidComponents ?: error("Failed to get androidComponents extension")

    if (androidComponentsExtension.pluginVersion >= SCOPED_ARTIFACTS_VERSION) {
      registerTransformTaskWithScopedArtifacts(taskProvider)
    } else {
      registerTransformTaskWithAllClassesTransform(taskProvider)
    }

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

  private fun Variant.registerTransformTaskWithAllClassesTransform(provider: TaskProvider<ParanoidTransformTask>) {
    @Suppress("DEPRECATION")
    artifacts.use(provider)
      .wiredWith(ParanoidTransformTask::inputDirectories, ParanoidTransformTask::outputDirectory)
      .toTransform(MultipleArtifact.ALL_CLASSES_DIRS)
  }

  private fun Variant.registerTransformTaskWithScopedArtifacts(provider: TaskProvider<ParanoidTransformTask>) {
    artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
      .use(provider)
      .toTransform(ScopedArtifact.CLASSES, ParanoidTransformTask::inputClasses, ParanoidTransformTask::inputDirectories, ParanoidTransformTask::output)
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

  private companion object {
    private val SCOPED_ARTIFACTS_VERSION = AndroidPluginVersion(major = 7, minor = 4, micro = 0)
    private val MINIMUM_VERSION = AndroidPluginVersion(major = 7, minor = 2, micro = 0)
  }
}

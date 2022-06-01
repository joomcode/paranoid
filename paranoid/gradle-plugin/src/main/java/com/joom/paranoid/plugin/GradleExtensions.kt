package com.joom.paranoid.plugin

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile

val Project.sourceSets: SourceSetContainer
  get() {
    val extension = extensions.getByType(JavaPluginExtension::class.java)
    return extension.sourceSets
  }

val Project.hasAndroid: Boolean
  get() = extensions.findByName("android") is BaseExtension
val Project.hasJava: Boolean
  get() = extensions.findByType(JavaPluginExtension::class.java) != null
val Project.android: BaseExtension
  get() = extensions.getByName("android") as BaseExtension
val Project.java: JavaPluginExtension
  get() = extensions.getByType(JavaPluginExtension::class.java)

val Project.androidComponents: AndroidComponentsExtension<*, *, *>?
  get() = applicationAndroidComponents ?: libraryAndroidComponents
val Project.applicationAndroidComponents: ApplicationAndroidComponentsExtension?
  get() = extensions.findByName("androidComponents") as? ApplicationAndroidComponentsExtension
val Project.libraryAndroidComponents: LibraryAndroidComponentsExtension?
  get() = extensions.findByName("androidComponents") as? LibraryAndroidComponentsExtension

inline fun <reified T : Task> Project.registerTask(name: String): TaskProvider<T> {
  return tasks.register(name, T::class.java)
}

inline fun <reified T : Task> Project.getTaskByName(name: String): TaskProvider<out T> {
  return tasks.named(name, T::class.java)
}

val SourceSetContainer.main: SourceSet
  get() = getByName("main")
val SourceSetContainer.test: SourceSet
  get() = getByName("test")

val TaskContainer.compileJava: JavaCompile
  get() = getByName("compileJava") as JavaCompile
val TaskContainer.compileTestJava: JavaCompile
  get() = getByName("compileTestJava") as JavaCompile

operator fun TaskContainer.get(name: String): Task? {
  return findByName(name)
}

fun Configuration.incomingJarArtifacts(componentFilter: ((ComponentIdentifier) -> Boolean)? = null): ArtifactCollection {
  return incoming
    .artifactView { configuration ->
      configuration.attributes { attributes ->
        @Suppress("UnstableApiUsage")
        attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, AndroidArtifacts.ArtifactType.CLASSES_JAR.type)
      }

      componentFilter?.let {
        configuration.componentFilter(it)
      }
    }
    .artifacts
}

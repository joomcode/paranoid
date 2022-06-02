package com.joom.paranoid.plugin

import com.android.build.gradle.BaseExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.compile.JavaCompile

val Project.sourceSets: SourceSetContainer
  get() {
    val extension = extensions.getByType(JavaPluginExtension::class.java)
    return extension.sourceSets
  }

val Project.hasAndroid: Boolean
  get() = extensions.findByName("android") is BaseExtension
val Project.android: BaseExtension
  get() = extensions.getByName("android") as BaseExtension

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

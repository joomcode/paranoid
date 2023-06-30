package com.joom.paranoid.plugin

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URI

class ParanoidPluginTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun `agp version with legacy transform not supported`() {
    val projectRoot = createProjectDirectory(agpVersion = "7.1.0")

    val result = createGradleRunner(projectRoot, GradleDistribution.GRADLE_7_5).buildAndFail()

    Assert.assertTrue("Should contain message", result.output.contains("Paranoid requires Android Gradle Plugin version 7.2.0"))
  }

  @Test
  fun `agp version with all classes transform`() {
    val projectRoot = createProjectDirectory(agpVersion = "7.2.0")

    val result = createGradleRunner(projectRoot, GradleDistribution.GRADLE_7_5).build()

    val tasks = result.parseDryRunExecution()
    Assert.assertTrue(tasks.any { it.path == ":paranoidTransformClassesDebug" })
  }

  @Test
  fun `agp version with scoped artifacts`() {
    val projectRoot = createProjectDirectory(agpVersion = "8.0.2")

    val result = createGradleRunner(projectRoot, GradleDistribution.GRADLE_8_0).build()

    val tasks = result.parseDryRunExecution()
    Assert.assertTrue(tasks.any { it.path == ":paranoidTransformClassesDebug" })
  }

  private fun createProjectDirectory(agpVersion: String): File {
    val projectRoot = temporaryFolder.newFolder()
    writeText(createBuildGradle(agpVersion), File(projectRoot, "build.gradle"))
    writeText(ANDROID_MANIFEST, File(projectRoot, "src/main/AndroidManifest.xml"))
    return projectRoot
  }

  private fun createGradleRunner(projectDir: File, gradle: GradleDistribution): GradleRunner {
    return GradleRunner.create()
      .withGradleDistribution(URI.create(gradle.url))
      .forwardOutput()
      .withProjectDir(projectDir)
      .withArguments("assembleDebug", "--dry-run", "--stacktrace")
  }

  private fun BuildResult.parseDryRunExecution(): List<BuildTask> {
    val split = output.split('\n')
    return split.mapNotNull {
      if (it.startsWith(":")) {
        val (path, _) = it.split(" ")
        TestBuildTask(path = path, outcome = TaskOutcome.SKIPPED)
      } else {
        null
      }
    }
  }

  private fun writeText(content: String, destination: File) {
    if (!destination.parentFile.exists() && !destination.parentFile.mkdirs()) {
      error("Failed to create parent directory ${destination.parentFile}")
    }

    destination.writeText(content)
  }

  @Language("gradle")
  private fun createBuildGradle(agpVersion: String, compileSdk: Int = 31, buildToolsVersion: String = "30.0.3"): String {
    return """
      buildscript {
        repositories {
          google()
          mavenLocal()
          mavenCentral()
        }

        dependencies {
          classpath "com.android.tools.build:gradle:$agpVersion"
          classpath "com.joom.paranoid:paranoid-gradle-plugin:+"
        }
      }

      apply plugin: "com.android.application"
      apply plugin: "com.joom.paranoid"

      repositories {
        google()
        mavenCentral()
      }

      android {
        compileSdk $compileSdk
        buildToolsVersion "$buildToolsVersion"

        defaultConfig {
          applicationId "com.joom.paranoid.test"
          namespace "com.joom.paranoid.test"
          versionCode 1
          versionName "1"
        }
      }
    """.trimIndent()
  }

  private companion object {
    @Language("xml")
    private const val ANDROID_MANIFEST = """
<?xml version="1.0" encoding="utf-8"?>
<manifest />
"""
  }

  private data class TestBuildTask(
    private val path: String,
    private val outcome: TaskOutcome,
  ) : BuildTask {
    override fun getPath(): String {
      return path
    }

    override fun getOutcome(): TaskOutcome {
      return outcome
    }

  }
}

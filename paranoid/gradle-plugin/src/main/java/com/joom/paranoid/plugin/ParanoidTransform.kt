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

@file:Suppress("DEPRECATION")
package com.joom.paranoid.plugin

import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.api.variant.VariantInfo
import com.joom.paranoid.processor.ParanoidProcessor
import java.io.File
import java.security.SecureRandom
import java.util.EnumSet

class ParanoidTransform(
  private val paranoid: ParanoidExtension
) : Transform() {

  override fun transform(invocation: TransformInvocation) {
    if (!invocation.isIncremental) {
      invocation.outputProvider.deleteAll()
    }

    val inputs = invocation.inputs.flatMap { it.jarInputs + it.directoryInputs }
    val outputs = inputs.map { input ->
      val format = if (input is JarInput) Format.JAR else Format.DIRECTORY
      invocation.outputProvider.getContentLocation(
        input.name,
        input.contentTypes,
        input.scopes,
        format
      )
    }

    val processor = ParanoidProcessor(
      obfuscationSeed = calculateObfuscationSeed(inputs),
      inputs = inputs.map { it.file },
      outputs = outputs,
      genPath = invocation.outputProvider.getContentLocation(
        "gen-paranoid",
        QualifiedContent.DefaultContentType.CLASSES,
        QualifiedContent.Scope.PROJECT,
        Format.DIRECTORY
      ),
      classpath = invocation.referencedInputs.flatMap { input ->
        input.jarInputs.map { it.file } + input.directoryInputs.map { it.file }
      },
      validationClasspath = emptyList(),
      bootClasspath = paranoid.bootClasspath,
      projectName = invocation.context.path.replace(":transformClassesWithParanoidFor", ":").replace(':', '$'),
      validateClasspath = false,
    )

    try {
      processor.process()
    } catch (exception: Exception) {
      throw TransformException(exception)
    }
  }

  override fun getName(): String {
    return "paranoid"
  }

  override fun getInputTypes(): Set<QualifiedContent.ContentType> {
    return EnumSet.of(QualifiedContent.DefaultContentType.CLASSES)
  }

  override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
    val scopes = EnumSet.of(QualifiedContent.Scope.PROJECT)
    if (paranoid.includeSubprojects) {
      scopes += QualifiedContent.Scope.SUB_PROJECTS
    }
    return scopes
  }

  override fun getReferencedScopes(): MutableSet<in QualifiedContent.Scope> {
    val scopes = EnumSet.of(
      QualifiedContent.Scope.EXTERNAL_LIBRARIES,
      QualifiedContent.Scope.PROVIDED_ONLY
    )

    if (!paranoid.includeSubprojects) {
      scopes += QualifiedContent.Scope.SUB_PROJECTS
    }
    return scopes
  }

  override fun isIncremental(): Boolean {
    return false
  }

  override fun isCacheable(): Boolean {
    return paranoid.isCacheable
  }

  @Suppress("UnstableApiUsage")
  override fun applyToVariant(variant: VariantInfo): Boolean {
    if (variant.isTest) {
      return false
    }

    return when (paranoid.applyToBuildTypes) {
      BuildType.NONE -> false
      BuildType.ALL -> true
      BuildType.NOT_DEBUGGABLE -> !variant.isDebuggable
    }
  }

  override fun getParameterInputs(): MutableMap<String, Any?> {
    return mutableMapOf(
      "version" to Build.VERSION,
      "includeSubprojects" to paranoid.includeSubprojects,
      "obfuscationSeed" to paranoid.obfuscationSeed,
      "applyToBuildTypes" to paranoid.applyToBuildTypes
    )
  }

  private fun TransformOutputProvider.getContentLocation(
    name: String,
    contentType: QualifiedContent.ContentType,
    scope: QualifiedContent.Scope,
    format: Format
  ): File {
    return getContentLocation(name, setOf(contentType), EnumSet.of(scope), format)
  }

  private fun calculateObfuscationSeed(inputs: List<QualifiedContent>): Int {
    val manuallySetObfuscationSeed = paranoid.obfuscationSeed
    return when {
      manuallySetObfuscationSeed != null -> manuallySetObfuscationSeed
      !paranoid.isCacheable -> SecureRandom().nextInt()
      else -> ObfuscationSeedCalculator.calculate(inputs) { it.file }
    }
  }
}

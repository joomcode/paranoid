package com.joom.paranoid.plugin

import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.variant.Variant
import org.gradle.api.tasks.TaskProvider

internal object AllClassesTransformRegisterAction {
  fun register(variant: Variant, provider: TaskProvider<ParanoidTransformTask>) {
    @Suppress("DEPRECATION")
    variant.artifacts.use(provider)
      .wiredWith(ParanoidTransformTask::inputDirectories, ParanoidTransformTask::outputDirectory)
      .toTransform(MultipleArtifact.ALL_CLASSES_DIRS)
  }
}

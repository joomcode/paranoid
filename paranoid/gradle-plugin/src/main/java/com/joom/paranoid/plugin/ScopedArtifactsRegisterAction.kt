package com.joom.paranoid.plugin

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import org.gradle.api.tasks.TaskProvider

internal object ScopedArtifactsRegisterAction {
  fun register(variant: Variant, provider: TaskProvider<ParanoidTransformTask>) {
    variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
      .use(provider)
      .toTransform(ScopedArtifact.CLASSES, ParanoidTransformTask::inputClasses, ParanoidTransformTask::inputDirectories, ParanoidTransformTask::output)
  }
}

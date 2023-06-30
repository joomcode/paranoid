package com.joom.paranoid.processor

import com.joom.grip.io.DirectoryFileSink
import com.joom.grip.io.EmptyFileSink
import com.joom.grip.io.FileSink
import com.joom.grip.io.JarFileSink
import java.io.File

internal fun createFileSink(outputFile: File): FileSink {
  return when (outputFile.fileType) {
    FileType.EMPTY -> EmptyFileSink
    FileType.DIRECTORY -> DirectoryFileSink(outputFile)
    FileType.JAR -> PatchedJarFileSink(JarFileSink(outputFile))
  }
}

private class PatchedJarFileSink(private val delegate: FileSink) : FileSink by delegate {
  private val directories = HashSet<String>()

  override fun createDirectory(path: String) {
    if (directories.add(path)) {
      delegate.createDirectory(path)
    }
  }
}

private val File.fileType: FileType
  get() = when {
    extension.endsWith("jar", ignoreCase = true) -> FileType.JAR
    !exists() || isDirectory -> FileType.DIRECTORY
    else -> error("Unknown file type for file $this")
  }

private enum class FileType {
  EMPTY,
  DIRECTORY,
  JAR
}

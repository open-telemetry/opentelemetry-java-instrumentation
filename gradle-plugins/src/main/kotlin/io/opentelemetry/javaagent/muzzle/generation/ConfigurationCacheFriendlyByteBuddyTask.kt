package io.opentelemetry.javaagent.muzzle.generation

import net.bytebuddy.build.Plugin
import net.bytebuddy.build.gradle.ByteBuddySimpleTask
import org.gradle.api.tasks.TaskAction
import java.io.IOException

/**
 * Byte Buddy task variant that avoids calling getProject() during task execution,
 * making it compatible with Gradle configuration cache. Can be removed once
 * https://github.com/raphw/byte-buddy/pull/1874 is released.
 */
open class ConfigurationCacheFriendlyByteBuddyTask : ByteBuddySimpleTask() {

  @TaskAction
  @Throws(IOException::class)
  override fun apply() {
    val sourceDir = source
    val targetDir = target

    if (sourceDir != targetDir && deleteRecursively(targetDir)) {
      logger.debug("Deleted all target files in {}", targetDir)
    }

    doApply(
      Plugin.Engine.Source.ForFolder(sourceDir),
      Plugin.Engine.Target.ForFolder(targetDir)
    )
  }
}

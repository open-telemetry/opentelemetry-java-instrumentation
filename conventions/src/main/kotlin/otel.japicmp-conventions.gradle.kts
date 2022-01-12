import me.champeau.gradle.japicmp.JapicmpTask

plugins {
  base

  id("me.champeau.gradle.japicmp")
}

/**
 * The latest *released* version of the project. Evaluated lazily so the work is only done if necessary.
 */
val latestReleasedVersion: String by lazy {
  // hack to find the current released version of the project
  val temp: Configuration = configurations.create("tempConfig")
  // pick the agent, since it's always there.
  dependencies.add(temp.name, "io.opentelemetry.javaagent:opentelemetry-javaagent:latest.release")
  val moduleVersion = configurations["tempConfig"].resolvedConfiguration.firstLevelModuleDependencies.elementAt(0).moduleVersion

  configurations.remove(temp)
  logger.info("Discovered latest release version: $moduleVersion")
  moduleVersion
}

/**
 * Locate the project's artifact of a particular version.
 */
fun findArtifact(version: String): File {
  val existingGroup = group
  try {
    val depModule = "${project.group}:${base.archivesName.get()}:$version@jar"
    // Temporarily change the group name because we want to fetch an artifact with the same
    // Maven coordinates as the project, which Gradle would not allow otherwise.
    group = "virtual_group"
    val depJar = "${base.archivesName.get()}-$version.jar"
    val configuration: Configuration = configurations.detachedConfiguration(
      dependencies.create(depModule)
    )
    return files(configuration.files).filter {
      it.name.equals(depJar)
    }.singleFile
  } finally {
    group = existingGroup
  }
}

tasks {
  val jApiCmp by registering(JapicmpTask::class) {
    dependsOn("jar")

    // the japicmp "new" version is either the user-specified one, or the locally built jar.
    val apiNewVersion: String? by project
    val newArtifact = apiNewVersion?.let { findArtifact(it) }
      ?: file(getByName<Jar>("jar").archiveFile)
    newClasspath = files(newArtifact)

    // only output changes, not everything
    isOnlyModified = true

    // the japicmp "old" version is either the user-specified one, or the latest release.
    val apiBaseVersion: String? by project
    val baselineVersion = apiBaseVersion ?: latestReleasedVersion
    oldClasspath = try {
      files(findArtifact(baselineVersion))
    } catch (e: Exception) {
      // if we can't find the baseline artifact, this is probably one that's never been published before,
      // so publish the whole API. We do that by flipping this flag, and comparing the current against nothing.
      isOnlyModified = false
      files()
    }

    // this is needed so that we only consider the current artifact, and not dependencies
    isIgnoreMissingClasses = true
    packageExcludes = listOf("*.internal", "*.internal.*")
    val baseVersionString = if (apiBaseVersion == null) "latest" else baselineVersion
    val newVersionString = if (apiNewVersion == null) "current" else apiNewVersion
    txtOutputFile = file("$rootDir/docs/apidiffs/${newVersionString}_vs_$baseVersionString/${base.archivesName.get()}.txt")
  }
}

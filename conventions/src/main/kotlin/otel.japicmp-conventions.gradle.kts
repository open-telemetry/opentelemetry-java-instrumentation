import japicmp.model.JApiChangeStatus
import me.champeau.gradle.japicmp.JapicmpTask
import me.champeau.gradle.japicmp.report.stdrules.RecordSeenMembersSetup
import me.champeau.gradle.japicmp.report.stdrules.SourceCompatibleRule
import me.champeau.gradle.japicmp.report.stdrules.UnchangedMemberRule

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
  temp.resolutionStrategy.cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
  // pick the bom, since we don't use dependency substitution on it.
  dependencies.add(temp.name, "io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:latest.release")
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

// generate the api diff report for any module that is stable
if (project.findProperty("otel.stable") == "true") {
  afterEvaluate {
    tasks {
      val jApiCmp by registering(JapicmpTask::class) {
        dependsOn("jar")

        // the japicmp "new" version is either the user-specified one, or the locally built jar.
        val apiNewVersion: String? by project
        val newArtifact = apiNewVersion?.let { findArtifact(it) }
          ?: file(getByName<Jar>("jar").archiveFile)
        newClasspath.from(files(newArtifact))

        // only output changes, not everything
        onlyModified.set(true)

        // the japicmp "old" version is either the user-specified one, or the latest release.
        val apiBaseVersion: String? by project
        val baselineVersion = apiBaseVersion ?: latestReleasedVersion
        oldClasspath.from(
          try {
            files(findArtifact(baselineVersion))
          } catch (e: Exception) {
            // if we can't find the baseline artifact, this is probably one that's never been published before,
            // so publish the whole API. We do that by flipping this flag, and comparing the current against nothing.
            onlyModified.set(false)
            files()
          }
        )

        // Reproduce defaults from https://github.com/melix/japicmp-gradle-plugin/blob/09f52739ef1fccda6b4310cf3f4b19dc97377024/src/main/java/me/champeau/gradle/japicmp/report/ViolationsGenerator.java#L130
        // only changing the BinaryIncompatibleRule to our custom one that allows new default methods
        // on interfaces, and adding default implementations to interface methods previously
        // abstract.
        richReport {
          addSetupRule(RecordSeenMembersSetup::class.java)
          addRule(JApiChangeStatus.NEW, SourceCompatibleRule::class.java)
          addRule(JApiChangeStatus.MODIFIED, SourceCompatibleRule::class.java)
          addRule(JApiChangeStatus.UNCHANGED, UnchangedMemberRule::class.java)
          addRule(SourceCompatibleRule::class.java)
        }

        // this is needed so that we only consider the current artifact, and not dependencies
        ignoreMissingClasses.set(true)
        packageExcludes.addAll("*.internal", "*.internal.*")
        val baseVersionString = if (apiBaseVersion == null) "latest" else baselineVersion
        txtOutputFile.set(
          apiNewVersion?.let { file("$rootDir/docs/apidiffs/${apiNewVersion}_vs_$baselineVersion/${base.archivesName.get()}.txt") }
            ?: file("$rootDir/docs/apidiffs/current_vs_$baseVersionString/${base.archivesName.get()}.txt")
        )
      }
      // have the jApiCmp task run every time the jar task is run, to make it more likely it will get used.
      named("jar") {
        finalizedBy(jApiCmp)
      }
    }
  }
}

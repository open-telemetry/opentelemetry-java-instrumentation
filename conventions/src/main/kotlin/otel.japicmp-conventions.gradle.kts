import com.google.auto.value.AutoValue
import japicmp.model.JApiChangeStatus
import japicmp.model.JApiClass
import japicmp.model.JApiCompatibility
import japicmp.model.JApiCompatibilityChangeType
import japicmp.model.JApiMethod
import me.champeau.gradle.japicmp.JapicmpTask
import me.champeau.gradle.japicmp.report.Violation
import me.champeau.gradle.japicmp.report.stdrules.AbstractRecordingSeenMembers
import me.champeau.gradle.japicmp.report.stdrules.BinaryIncompatibleRule
import me.champeau.gradle.japicmp.report.stdrules.RecordSeenMembersSetup
import me.champeau.gradle.japicmp.report.stdrules.SourceCompatibleRule
import me.champeau.gradle.japicmp.report.stdrules.UnchangedMemberRule
import org.gradle.api.artifacts.ResolveException
import org.gradle.internal.resolve.ModuleVersionNotFoundException

plugins {
  base

  id("me.champeau.gradle.japicmp")
}

/**
 * The baseline version japicmp compares against. Pinned in version.gradle.kts and bumped
 * atomically with docs/apidiffs/current_vs_latest/ by the post-release PR opened from
 * .github/workflows/release.yml.
 */
val apidiffBaselineVersion: String =
  rootProject.extra["apidiffBaselineVersion"] as String

class AllowNewAbstractMethodOnAutovalueClasses : AbstractRecordingSeenMembers() {
  override fun maybeAddViolation(member: JApiCompatibility): Violation? {
    val allowableAutovalueChanges =
      setOf(
        JApiCompatibilityChangeType.METHOD_ABSTRACT_ADDED_TO_CLASS,
        JApiCompatibilityChangeType.METHOD_ADDED_TO_PUBLIC_CLASS,
        JApiCompatibilityChangeType.ANNOTATION_ADDED,
      )
    if (
      member.compatibilityChanges.all { it.type in allowableAutovalueChanges } &&
      member is JApiMethod &&
      isAutoValueClass(member.getjApiClass())
    ) {
      return Violation.accept(member, "AutoValue will automatically add implementation")
    }
    if (member.compatibilityChanges.isEmpty() && member is JApiClass && isAutoValueClass(member)) {
      return Violation.accept(member, "AutoValue class modification is allowed")
    }
    return null
  }

  private fun isAutoValueClass(japiClass: JApiClass): Boolean {
    val newClass = japiClass.newClass.orElse(null) ?: return false
    return newClass.getAnnotation(AutoValue::class.java) != null ||
      newClass.getAnnotation(AutoValue.Builder::class.java) != null
  }
}

class SourceIncompatibleRule : AbstractRecordingSeenMembers() {
  override fun maybeAddViolation(member: JApiCompatibility): Violation? {
    if (!member.isSourceCompatible()) {
      return Violation.error(member, "Not source compatible: $member")
    }
    return null
  }
}

fun getAllPublishedModules(): List<Project> = project.rootProject.allprojects
  .filter {
    it.plugins.hasPlugin("otel.publish-conventions") &&
      it.findProperty("otel.stable") == "true" &&
      !it.hasProperty("otel.release") &&
      it.tasks.findByName("jar") != null
  }
  .toList()

fun getClasspathForVersion(version: String, allowMissingArtifacts: Boolean = false): List<File> {
  val publishedModules =
    getAllPublishedModules().map {
      Pair(
        it.findProperty("mavenGroupId") as String? ?: it.group.toString(),
        it.base.archivesName.get(),
      )
    }
  val existingGroup = group
  group = "virtual_group"
  try {
    return publishedModules
      .mapNotNull { (moduleGroup, archiveName) ->
        val depModule = "$moduleGroup:$archiveName:$version@jar"
        val depJar = "$archiveName-$version.jar"
        val configuration: Configuration =
          configurations.detachedConfiguration(
            dependencies.create(depModule),
          )
        try {
          files(configuration.files)
            .filter { file -> file.name == depJar }
            .singleFile
        } catch (e: ResolveException) {
          // Stable artifacts introduced after the baseline have no old jar to contribute.
          if (allowMissingArtifacts && e.cause is ModuleVersionNotFoundException) {
            null
          } else {
            throw e
          }
        }
      }
      .toList()
  } finally {
    group = existingGroup
  }
}

fun getCurrentClassPath(): List<File> = getAllPublishedModules()
  .map { it.tasks.getByName<Jar>("jar").archiveFile.get().asFile }
  .toList()

// generate the api diff report for any module that is stable
if (project.findProperty("otel.stable") == "true" && project.path != ":javaagent") {
  afterEvaluate {
    // Only apply japicmp to projects that have a jar task (i.e. not BOMs or platforms)
    tasks.findByName("jar")?.let {
      tasks {
        val jApiCmp = register<JapicmpTask>("jApiCmp") {
          getAllPublishedModules().forEach {
            dependsOn(it.tasks.getByName("jar"))
          }

          // The japicmp "new" version is either the user-specified one, or the locally built jar.
          val apiNewVersion = project.findProperty("apiNewVersion") as String?
          val apiBaseVersion = project.findProperty("apiBaseVersion") as String?
          val baselineVersion = apiBaseVersion ?: apidiffBaselineVersion

          val archiveName = base.archivesName.get()
          val newModuleClasspath =
            apiNewVersion?.let { getClasspathForVersion(it) } ?: getCurrentClassPath()
          val oldModuleClasspath = getClasspathForVersion(baselineVersion, allowMissingArtifacts = true)
          val archivePattern = (archiveName + "-([0-9\\.]*)(-SNAPSHOT)?.jar").toRegex()
          val newArchive = newModuleClasspath.singleOrNull { it.name.matches(archivePattern) }
          val oldArchive = oldModuleClasspath.singleOrNull { it.name.matches(archivePattern) }
          newClasspath.from(newModuleClasspath)
          oldClasspath.from(if (oldArchive == null) emptyList<File>() else oldModuleClasspath)
          newArchives.from(newArchive)
          oldArchives.from(oldArchive)

          // If this artifact is new, compare against nothing and publish its complete API.
          onlyModified.set(oldArchive != null)

          // Reproduce defaults from japicmp-gradle-plugin 0.4.6 with compatibility adjustments.
          // Generics are not detected correctly.
          // Allow adding default methods on interfaces.
          // Allow adding default implementations for default methods.
          // japicmp 0.23.1 does not recognize default methods of superinterfaces.
          compatibilityChangeExcludes.set(
            listOf(
              JApiCompatibilityChangeType.CLASS_GENERIC_TEMPLATE_CHANGED.name,
              JApiCompatibilityChangeType.METHOD_NEW_DEFAULT.name,
              JApiCompatibilityChangeType.METHOD_ABSTRACT_NOW_DEFAULT.name,
              JApiCompatibilityChangeType.METHOD_ABSTRACT_ADDED_IN_IMPLEMENTED_INTERFACE.name,
            ),
          )

          richReport {
            addSetupRule(RecordSeenMembersSetup::class.java)
            addRule(JApiChangeStatus.NEW, SourceCompatibleRule::class.java)
            addRule(JApiChangeStatus.MODIFIED, SourceCompatibleRule::class.java)
            addRule(JApiChangeStatus.UNCHANGED, UnchangedMemberRule::class.java)
            addRule(AllowNewAbstractMethodOnAutovalueClasses::class.java)
            addRule(BinaryIncompatibleRule::class.java)
            addRule(SourceIncompatibleRule::class.java)
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
        // have the check task depend on the api comparison task, to make it more likely it will get used.
        named("check") {
          dependsOn(jApiCmp)
        }
      }
    }
  }
}

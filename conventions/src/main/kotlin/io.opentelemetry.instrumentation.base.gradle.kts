/** Common setup for manual instrumentation of libraries and javaagent instrumentation. */

plugins {
  `java-library`
}

/**
 * We define three dependency configurations to use when adding dependencies to libraries being
 * instrumented.
 *
 * - library: A dependency on the instrumented library. Results in the dependency being added to
 *     compileOnly and testImplementation. If the build is run with -PtestLatestDeps=true, the
 *     version when added to testImplementation will be overridden by `+`, the latest version
 *     possible. For simple libraries without different behavior between versions, it is possible
 *     to have a single dependency on library only.
 *
 * - testLibrary: A dependency on a library for testing. This will usually be used to either
 *     a) use a different version of the library for compilation and testing and b) to add a helper
 *     that is only required for tests (e.g., library-testing artifact). The dependency will be
 *     added to testImplementation and will have a version of `+` when testing latest deps as
 *     described above.
 *
 * - latestDepTestLibrary: A dependency on a library for testing when testing of latest dependency
 *   version is enabled. This dependency will be added as-is to testImplementation, but only if
 *   -PtestLatestDeps=true. The version will not be modified but it will be given highest
 *   precedence. Use this to restrict the latest version dependency from the default `+`, for
 *   example to restrict to just a major version by specifying `2.+`.
 */

val testLatestDeps = gradle.startParameter.projectProperties["testLatestDeps"] == "true"
val resolveLatestDeps = gradle.startParameter.projectProperties["resolveLatestDeps"] == "true"
val pinLatestDeps = testLatestDeps && !resolveLatestDeps
extra["testLatestDeps"] = testLatestDeps

fun getPinnedVersions(): Map<String, String> {
  if (!pinLatestDeps) return emptyMap()
  val key = "latestDepPinnedVersions"
  if (!rootProject.extra.has(key)) {
    val file = rootProject.file(".github/config/latest-dep-versions.json")
    @Suppress("UNCHECKED_CAST")
    rootProject.extra[key] = if (file.exists()) {
      groovy.json.JsonSlurper().parse(file) as Map<String, String>
    } else {
      emptyMap<String, String>()
    }
  }
  @Suppress("UNCHECKED_CAST")
  return rootProject.extra[key] as Map<String, String>
}

fun lookupPinnedVersion(group: String?, name: String, version: String?): String? {
  if (!pinLatestDeps || group == null) return null
  val pinned = getPinnedVersions()
  return if (version == "latest.release") {
    pinned["$group:$name"]
  } else if (version != null && version.contains("+")) {
    pinned["$group:$name#$version"] ?: pinned["$group:$name"]
  } else {
    null
  }
}

@CacheableRule
abstract class TestLatestDepsRule : ComponentMetadataRule {
  override fun execute(context: ComponentMetadataContext) {
    val version = context.details.id.version
    if (version.contains("-alpha", true)
      || version.contains("-beta", true)
      || version.contains("-rc", true)
      || version.contains(".rc", true)
      || version.contains("-m", true) // e.g. spring milestones are published to grails repo
      || version.contains(".m", true) // e.g. lettuce
      || version.contains(".alpha", true) // e.g. netty
      || version.contains(".beta", true) // e.g. hibernate
      || version.contains(".cr", true) // e.g. hibernate
      || version.endsWith("-nf-execution") // graphql
      || GIT_SHA_PATTERN.matches(version) // graphql
      || DATETIME_PATTERN.matches(version) // graphql
    ) {
      context.details.status = "milestone"
    }
  }

  companion object {
    private val GIT_SHA_PATTERN = Regex("^.*-[0-9a-f]{7,}$")
    private val DATETIME_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}.*$")
  }
}

configurations {
  val library by creating {
    isCanBeResolved = false
    isCanBeConsumed = false
  }
  val testLibrary by creating {
    isCanBeResolved = false
    isCanBeConsumed = false
  }
  val latestDepTestLibrary by creating {
    isCanBeResolved = false
    isCanBeConsumed = false
  }

  val testImplementation by getting

  listOf(library, testLibrary).forEach { configuration ->
    // We use whenObjectAdded and copy into the real configurations instead of extension to allow
    // mutating the version for latest dep tests.
    configuration.dependencies.whenObjectAdded {
      val dep = copy()
      if (testLatestDeps) {
        val extDep = this as ExternalDependency
        val pinnedVersion = lookupPinnedVersion(extDep.group, extDep.name, "latest.release")
        (dep as ExternalDependency).version {
          require(pinnedVersion ?: "latest.release")
        }
      }
      testImplementation.dependencies.add(dep)
    }
  }
  if (testLatestDeps) {
    dependencies {
      components {
        all<TestLatestDepsRule>()
      }
    }

    latestDepTestLibrary.dependencies.whenObjectAdded {
      val dep = copy()
      val declaredVersion = dep.version
      if (declaredVersion != null) {
        val extDep = this as ExternalDependency
        val pinnedVersion = lookupPinnedVersion(extDep.group, extDep.name, declaredVersion)
        (dep as ExternalDependency).version {
          strictly(pinnedVersion ?: declaredVersion)
        }
      }
      testImplementation.dependencies.add(dep)
    }
  }
  named("compileOnly") {
    extendsFrom(library)
  }

  if (pinLatestDeps) {
    configureEach {
      if (isCanBeResolved) {
        resolutionStrategy.eachDependency {
          val pinnedVersion = lookupPinnedVersion(requested.group, requested.name, requested.version)
          if (pinnedVersion != null) {
            useVersion(pinnedVersion)
          }
        }
      }
    }
  }
}

if (testLatestDeps) {
  afterEvaluate {
    tasks {
      withType<JavaCompile>().configureEach {
        with(options) {
          // We may use methods that are deprecated in future versions, we check lint on the normal
          // build and don't need this for testLatestDeps.
          compilerArgs.add("-Xlint:-deprecation")
        }
      }
    }

    if (tasks.names.contains("latestDepTest")) {
      val latestDepTest by tasks.existing
      tasks.named("test").configure {
        dependsOn(latestDepTest)
      }
    }
  }
} else {
  afterEvaluate {
    // Disable compiling latest dep tests for non latest dep builds in CI. This is needed to avoid
    // breaking build because of a new library version which could force backporting latest dep
    // fixes to release branches.
    // This is only needed for modules where base version and latest dep tests use a different
    // source directory.
    var latestDepCompileTaskNames = arrayOf("compileLatestDepTestJava", "compileLatestDepTestGroovy", "compileLatestDepTestScala")
    for (compileTaskName in latestDepCompileTaskNames) {
      if (tasks.names.contains(compileTaskName)) {
        tasks.named(compileTaskName).configure {
          enabled = false
        }
      }
    }
  }
}

tasks {
  val generateInstrumentationVersionFile by registering {
    val name = computeInstrumentationName()
    val version = project.version as String
    inputs.property("instrumentation.name", name)
    inputs.property("instrumentation.version", version)

    val propertiesDir = layout.buildDirectory.dir("generated/instrumentationVersion/META-INF/io/opentelemetry/instrumentation/")
    outputs.dir(propertiesDir)

    doLast {
      File(propertiesDir.get().asFile, "$name.properties").writeText("version=$version")
    }
  }
}

fun computeInstrumentationName(): String {
  val name = when (projectDir.name) {
    "javaagent", "library", "library-autoconfigure" -> projectDir.parentFile.name
    else -> project.name
  }
  return "io.opentelemetry.$name"
}

sourceSets {
  main {
    output.dir("build/generated/instrumentationVersion", "builtBy" to "generateInstrumentationVersionFile")
  }
}

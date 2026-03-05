import io.opentelemetry.instrumentation.gradle.VersionFilters

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
 *   version is enabled. This dependency will be added to testImplementation only if
 *   -PtestLatestDeps=true. Its version overrides the `latest.release` from library/testLibrary
 *   via resolutionStrategy.eachDependency (which takes highest precedence in Gradle). Use this
 *   to restrict the latest version to a specific range, e.g. `2.+` to stay on major version 2.
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
    if (VersionFilters.isUnstable(context.details.id.version)) {
      context.details.status = "milestone"
    }
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

  // Collect latestDepTestLibrary overrides so we can apply them via resolutionStrategy.
  // This map is populated during configuration and read during resolution.
  val latestDepTestLibraryOverrides = mutableMapOf<String, String>()

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

    // latestDepTestLibrary lets modules restrict the latest version to a specific range
    // (e.g. "3.+" to stay on major version 3). These constraints must beat the
    // require("latest.release") from library/testLibrary above.
    //
    // We can't use strictly() on the dependency itself because it conflicts with require()
    // from library/testLibrary (Gradle rejects conflicting strict/require constraints).
    // We can't use prefer() on library/testLibrary either because prefer() is too weak and
    // loses against the original library() version from compileOnly.
    //
    // Instead, we collect latestDepTestLibrary versions here and apply them via
    // resolutionStrategy.eachDependency below, which overrides all other constraints.
    latestDepTestLibrary.dependencies.whenObjectAdded {
      val dep = copy()
      val declaredVersion = dep.version
      if (declaredVersion != null) {
        val extDep = this as ExternalDependency
        val pinnedVersion = lookupPinnedVersion(extDep.group, extDep.name, declaredVersion)
        val resolvedVersion = pinnedVersion ?: declaredVersion
        // Record the override; the actual version forcing happens in eachDependency below.
        // We use require() here (not strictly()) because strictly() would conflict with
        // the require() from library/testLibrary for the same artifact. The eachDependency
        // callback enforces the final version regardless.
        latestDepTestLibraryOverrides["${extDep.group}:${extDep.name}"] = resolvedVersion
        (dep as ExternalDependency).version {
          require(resolvedVersion)
        }
      }
      testImplementation.dependencies.add(dep)
    }
  }
  named("compileOnly") {
    extendsFrom(library)
  }

  // Apply version overrides via resolutionStrategy which takes highest precedence.
  // This handles two cases:
  // 1. latestDepTestLibraryOverrides: modules that restrict latest deps to a version range
  //    (e.g. spring-boot-starter-test:3.+ while latest is 4.x). These must override
  //    the require("latest.release") from library/testLibrary.
  // 2. pinLatestDeps pinned versions: when pinning is enabled, any dependency still using
  //    "latest.release" or "+" versions (e.g. transitive deps) gets pinned to a concrete
  //    version from the JSON file.
  if (testLatestDeps) {
    // Only apply to test-related configurations, not build tool configurations like Zinc
    // (the Scala compiler). Overriding scala-library in Zinc's configuration breaks compilation.
    configureEach {
      if (isCanBeResolved && (name.startsWith("test") || name.startsWith("latestDepTest"))) {
        resolutionStrategy.eachDependency {
          // latestDepTestLibrary overrides take priority over pinned versions
          val override = latestDepTestLibraryOverrides["${requested.group}:${requested.name}"]
          if (override != null) {
            useVersion(override)
            return@eachDependency
          }
          if (pinLatestDeps) {
            val pinnedVersion = lookupPinnedVersion(requested.group, requested.name, requested.version)
            if (pinnedVersion != null) {
              useVersion(pinnedVersion)
            }
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

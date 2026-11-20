// Shared GraalVM native-image test configuration for the smoke-tests-otel-starter Spring Boot
// modules. Centralizes the workarounds we need with native-build-tools:
//
//  * The metadata repository is resolved through a detached configuration that violates Gradle's
//    project lock requirements (see https://github.com/graalvm/native-build-tools/issues/572 and
//    https://github.com/gradle/gradle/issues/17559). We download the metadata zip ourselves and
//    point the extension at a project-local copy.
//  * `collectReachabilityMetadata` triggers the same isolation issue and is disabled.

import io.opentelemetry.instrumentation.gradle.NativeMetadataRepository
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.graalvm.buildtools.gradle.dsl.GraalVMReachabilityMetadataRepositoryExtension

// Keep this in sync with the org.graalvm.buildtools.native plugin version in settings.gradle.kts.
val graalvmReachabilityMetadataVersion = "1.1.19"

if (gradle.startParameter.taskNames.any { it.contains("nativeTest") }) {
  apply(plugin = "org.graalvm.buildtools.native")
}

val repositoryMetadata = configurations.create("repositoryMetadata")

dependencies {
  repositoryMetadata(
    "org.graalvm.buildtools:graalvm-reachability-metadata:" +
      "$graalvmReachabilityMetadataVersion:repository@zip"
  )
}

plugins.withId("org.graalvm.buildtools.native") {
  extensions.configure<GraalVMExtension>("graalvmNative") {
    binaries.named("test") {
      quickBuild.set(true)
    }
  }

  dependencies {
    // javac 25 warns when JUnit's API Guardian annotation type is missing from this classpath.
    add("aotTestCompileOnly", "org.apiguardian:apiguardian-api:1.1.2")
  }

  tasks.named<JavaCompile>("compileAotJava").configure {
    with(options) {
      // Spring-generated AOT sources emit warnings that fail compilation with -Werror.
      // Explicit exclusions are needed alongside none; none alone does not suppress them.
      compilerArgs.add("-Xlint:-deprecation,-unchecked,-rawtypes,none")
    }
  }
  tasks.named<JavaCompile>("compileAotTestJava").configure {
    with(options) {
      // Spring-generated AOT sources emit warnings that fail compilation with -Werror.
      // Explicit exclusions are needed alongside none; none alone does not suppress them.
      compilerArgs.add("-Xlint:-deprecation,-unchecked,-rawtypes,none")
    }
  }
  tasks.named("checkstyleAot").configure {
    enabled = false
  }
  tasks.named("checkstyleAotTest").configure {
    enabled = false
  }

  // Resolve while this project is being configured, rather than from the plugin's detached
  // configuration during task graph construction. The plugin queries its metadata inputs while
  // discovering task dependencies, so a prerequisite task would run too late. Its service
  // extracts this local archive before querying it.
  val metadataArchive = repositoryMetadata.singleFile
  val metadataRepository = providers.of(NativeMetadataRepository::class) {
    parameters.archive.set(metadataArchive)
    // The plugin keys its extraction cache by URI, so sibling projects must use different paths.
    parameters.destination.set(layout.buildDirectory.dir("metadata-repository"))
  }

  // See https://github.com/graalvm/native-build-tools/issues/572
  (extensions.getByName("graalvmNative") as ExtensionAware).extensions
    .configure<GraalVMReachabilityMetadataRepositoryExtension> {
      enabled.set(true)
      // Manually set up the metadata repository to avoid resolving the default repository, which
      // currently fails with "Resolution of the configuration ... was attempted without an
      // exclusive lock. This is unsafe and not allowed."
      uri.set(metadataRepository)
    }

  tasks.named<Test>("test").configure {
    useJUnitPlatform()
    setForkEvery(1)
  }

  // Disable collectReachabilityMetadata task to avoid configuration isolation issues
  // See https://github.com/gradle/gradle/issues/17559
  tasks.named("collectReachabilityMetadata").configure {
    enabled = false
  }
}

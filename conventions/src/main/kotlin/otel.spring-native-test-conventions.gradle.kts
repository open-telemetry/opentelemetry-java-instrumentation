// Shared GraalVM native-image test configuration for the smoke-tests-otel-starter Spring Boot
// modules. Centralizes the workarounds we need with native-build-tools 1.0.0:
//
//  * The metadata repository is resolved through a detached configuration that violates Gradle's
//    project lock requirements (see https://github.com/graalvm/native-build-tools/issues/572 and
//    https://github.com/gradle/gradle/issues/17559). We download the metadata zip ourselves and
//    point the extension at the extracted directory.
//  * `collectReachabilityMetadata` triggers the same isolation issue and is disabled.

import org.graalvm.buildtools.gradle.dsl.GraalVMReachabilityMetadataRepositoryExtension

// Keep this in sync with the org.graalvm.buildtools.native plugin version in settings.gradle.kts.
val graalvmReachabilityMetadataVersion = "1.1.0"

if (gradle.startParameter.taskNames.any { it.contains("nativeTest") }) {
  apply(plugin = "org.graalvm.buildtools.native")
}

val repositoryMetadata: Configuration by configurations.creating

dependencies {
  repositoryMetadata(
    "org.graalvm.buildtools:graalvm-reachability-metadata:" +
      "$graalvmReachabilityMetadataVersion:repository@zip"
  )
}

plugins.withId("org.graalvm.buildtools.native") {
  tasks.named<JavaCompile>("compileAotJava").configure {
    with(options) {
      compilerArgs.add("-Xlint:-deprecation,-unchecked,none")
      // To disable warnings/failure coming from the Java compiler during the Spring AOT processing
      // -deprecation,-unchecked and none are required (none is not enough)
    }
  }
  tasks.named<JavaCompile>("compileAotTestJava").configure {
    with(options) {
      compilerArgs.add("-Xlint:-deprecation,-unchecked,none")
      // To disable warnings/failure coming from the Java compiler during the Spring AOT processing
      // -deprecation,-unchecked and none are required (none is not enough)
    }
  }
  tasks.named("checkstyleAot").configure {
    enabled = false
  }
  tasks.named("checkstyleAotTest").configure {
    enabled = false
  }

  // Use a per-project output directory so parallel builds of sibling smoke-test modules don't
  // race on a single shared destination.
  val metadataRepoDir = layout.buildDirectory.dir("metadata-repository")
  val extractRepositoryMetadata by tasks.registering(Copy::class) {
    from({ zipTree(repositoryMetadata.singleFile) })
    into(metadataRepoDir)
  }
  tasks.named("nativeTestCompile").configure {
    dependsOn(extractRepositoryMetadata)
  }

  // See https://github.com/graalvm/native-build-tools/issues/572
  (extensions.getByName("graalvmNative") as ExtensionAware).extensions
    .configure<GraalVMReachabilityMetadataRepositoryExtension> {
      enabled.set(false)
      // Manually set up the metadata repository to avoid resolving the default repository, which
      // currently fails with "Resolution of the configuration ... was attempted without an
      // exclusive lock. This is unsafe and not allowed."
      uri.set(metadataRepoDir.map { it.asFile.toURI() })
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

plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("io.ratpack:ratpack-core:1.4.0")

  testImplementation(project(":instrumentation:ratpack-1.4:testing"))

  // 1.4.0 has a bug which makes tests flaky
  // (https://github.com/ratpack/ratpack/commit/dde536ac138a76c34df03a0642c88d64edde688e)
  testLibrary("io.ratpack:ratpack-test:1.4.1")
  testLibrary("io.ratpack:ratpack-guice:1.4.0")

  if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_11)) {
    testImplementation("com.sun.activation:jakarta.activation:1.2.2")
  }
}

// Requires old Guava. Can't use enforcedPlatform since predates BOM
configurations.testRuntimeClasspath.resolutionStrategy.force("com.google.guava:guava:19.0")

// to allow all tests to pass we need to choose a specific netty version
if (!(findProperty("testLatestDeps") as Boolean)) {
  configurations.configureEach {
    if (!name.contains("muzzle")) {
      resolutionStrategy {
        eachDependency {
          // specifying a fixed version for all libraries with io.netty group
          if (requested.group == "io.netty") {
            useVersion("4.1.31.Final")
          }
        }
      }
    }
  }
}

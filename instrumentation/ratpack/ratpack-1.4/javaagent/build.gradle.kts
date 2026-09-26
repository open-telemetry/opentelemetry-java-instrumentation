plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.ratpack")
    module.set("ratpack-core")
    versions.set("[1.4.0,)")
    excludeInstrumentationName("ratpack-1.7")
  }
  pass {
    name.set("Ratpack 1.7 instrumentation")
    group.set("io.ratpack")
    module.set("ratpack-core")
    versions.set("[1.7.0,)")
    assertInverse.set(true)
    excludeInstrumentationName("ratpack-1.4")
    excludeInstrumentationName("netty-4.1")
  }
}

dependencies {
  library("io.ratpack:ratpack-core:1.4.0")
  compileOnly("io.ratpack:ratpack-core:1.7.0")

  implementation(project(":instrumentation:netty:netty-4.1:javaagent"))
  implementation(project(":instrumentation:netty:netty-4.1:library"))
  implementation(project(":instrumentation:ratpack:ratpack-1.7:library"))

  testImplementation(project(":instrumentation:ratpack:ratpack-1.4:testing"))

  // 1.4.0 has a bug which makes tests flaky
  // (https://github.com/ratpack/ratpack/commit/dde536ac138a76c34df03a0642c88d64edde688e)
  testLibrary("io.ratpack:ratpack-test:1.4.1")

  if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_11)) {
    testImplementation("com.sun.activation:jakarta.activation:1.2.2")
  }

  latestDepTestLibrary("io.ratpack:ratpack-core:1.6.+") // see test suite below
  latestDepTestLibrary("io.ratpack:ratpack-test:1.6.+") // see test suite below
}

// Requires old Guava. Can't use enforcedPlatform since predates BOM
if (!otelProps.testLatestDeps) {
  configurations.testRuntimeClasspath.get().resolutionStrategy.force("com.google.guava:guava:19.0")
}

// to allow all tests to pass we need to choose a specific netty version
listOf("testCompileClasspath", "testRuntimeClasspath").forEach {
  configurations.named(it) {
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

val library17Test = testing.suites.register<JvmTestSuite>("library17Test") {
  dependencies {
    implementation(project(":instrumentation:ratpack:ratpack-1.4:testing"))
    implementation(project(":instrumentation:ratpack:ratpack-1.7:library"))
    val ratpackVersion = baseVersion("1.7.0").orLatest()
    implementation("io.ratpack:ratpack-core:$ratpackVersion")
    implementation("io.ratpack:ratpack-test:$ratpackVersion")
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
    systemProperty("collectMetadata", otelProps.collectMetadata)
    systemProperty("metadataConfig", "otel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }

  test {
    systemProperty("ratpack14Test", true) // used in AbstractRatpackHttpClientTest
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
    systemProperty("ratpack14Test", true) // used in AbstractRatpackHttpClientTest
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=service.peer")
  }

  val library17TestStableSemconv = register<Test>("library17TestStableSemconv") {
    testClassesDirs = library17Test.get().sources.output.classesDirs
    classpath = library17Test.get().sources.runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=service.peer")
  }

  check {
    dependsOn(testing.suites, testStableSemconv, library17TestStableSemconv)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}

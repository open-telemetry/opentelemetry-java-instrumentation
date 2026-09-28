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
    // instrumentation-docs:ignore - verification only, the directive above is the range we document
    name.set("Ratpack HTTP client instrumentation")
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
}

// The 1.4 tests require old Guava and Netty versions.
if (!otelProps.testLatestDeps) {
  configurations.testRuntimeClasspath.get().resolutionStrategy.force("com.google.guava:guava:19.0")

  listOf("testCompileClasspath", "testRuntimeClasspath").forEach {
    configurations.named(it) {
      resolutionStrategy {
        eachDependency {
          if (requested.group == "io.netty") {
            useVersion("4.1.31.Final")
          }
        }
      }
    }
  }
}

val latestDepTest = testing.suites.register<JvmTestSuite>("latestDepTest") {
  dependencies {
    implementation(project(":instrumentation:ratpack:ratpack-1.4:testing"))
    implementation(project(":instrumentation:ratpack:ratpack-1.7:library"))
    val ratpackVersion = baseVersion("1.7.0").orLatest()
    implementation("io.ratpack:ratpack-core:$ratpackVersion")
    implementation("io.ratpack:ratpack-test:$ratpackVersion")
  }
}

tasks {
  if (otelProps.testLatestDeps) {
    named("compileTestJava") {
      enabled = false
    }
  }

  processResources {
    // The newer API emits its own scope, which needs a version resource as well.
    from(named("generateInstrumentationVersionFile")) {
      include("io.opentelemetry.ratpack-1.4.properties")
      rename { "io.opentelemetry.ratpack-1.7.properties" }
      into("META-INF/io/opentelemetry/instrumentation")
    }
  }

  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    jvmArgs("-Dotel.instrumentation.common.controller-telemetry.enabled=true")
    systemProperty("collectMetadata", otelProps.collectMetadata)
    systemProperty("metadataConfig", "otel.instrumentation.common.controller-telemetry.enabled=true")
  }

  test {
    systemProperty("ratpack14Test", true) // used in AbstractRatpackHttpClientTest
    enabled = !otelProps.testLatestDeps
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
    systemProperty("ratpack14Test", true) // used in AbstractRatpackHttpClientTest
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=service.peer")
    enabled = !otelProps.testLatestDeps
  }

  named<Test>("latestDepTest") {
    enabled = otelProps.testLatestDeps
  }

  val latestDepTestStableSemconv = register<Test>("latestDepTestStableSemconv") {
    testClassesDirs = latestDepTest.get().sources.output.classesDirs
    classpath = latestDepTest.get().sources.runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=service.peer")
    enabled = otelProps.testLatestDeps
  }

  val latestDepTestV3Preview = register<Test>("latestDepTestV3Preview") {
    testClassesDirs = latestDepTest.get().sources.output.classesDirs
    classpath = latestDepTest.get().sources.runtimeClasspath
    filter {
      includeTestsMatching("*RatpackHttpClientTest.durationMetricHasProtocolVersion")
    }
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true,otel.instrumentation.common.controller-telemetry.enabled=true")
    enabled = otelProps.testLatestDeps
  }

  check {
    dependsOn(testing.suites, testStableSemconv, latestDepTestStableSemconv, latestDepTestV3Preview)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}

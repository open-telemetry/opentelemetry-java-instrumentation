plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.ratpack")
    module.set("ratpack-core")
    versions.set("[1.4.0,)")
  }
}

dependencies {
  library("io.ratpack:ratpack-core:1.4.0")

  implementation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testImplementation(project(":instrumentation:ratpack-1.4:testing"))

  // 1.4.0 has a bug which makes tests flaky
  // (https://github.com/ratpack/ratpack/commit/dde536ac138a76c34df03a0642c88d64edde688e)
  testLibrary("io.ratpack:ratpack-test:1.4.1")

  if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_11)) {
    testImplementation("com.sun.activation:jakarta.activation:1.2.2")
  }
}

// Requires old Guava. Can't use enforcedPlatform since predates BOM
configurations.testRuntimeClasspath.resolutionStrategy.force("com.google.guava:guava:19.0")

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
}

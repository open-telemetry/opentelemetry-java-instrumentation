plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.glassfish.grizzly")
    module.set("grizzly-http")
    versions.set("[2.3,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.glassfish.grizzly:grizzly-http:2.3")

  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  testLibrary("org.glassfish.grizzly:grizzly-http-server:2.3")
}

tasks {
  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=http")
  }

  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.grizzly.enabled=true")

    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }

  check {
    dependsOn(testStableSemconv)
  }
}

// Requires old Guava. Can't use enforcedPlatform since predates BOM
configurations.testRuntimeClasspath.get().resolutionStrategy.force("com.google.guava:guava:19.0")

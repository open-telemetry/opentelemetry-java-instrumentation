plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.github.resilience4j")
    module.set("resilience4j-circuitbreaker")
    versions.set("[2.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.github.resilience4j:resilience4j-circuitbreaker:2.0.0")
  latestDepTestLibrary("io.github.resilience4j:resilience4j-circuitbreaker:latest.release")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs(
      "-Dotel.instrumentation.resilience4j-circuitbreaker.experimental-span-attributes=true"
    )
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.resilience4j-circuitbreaker.experimental-span-attributes=true"
    )
  }

  check {
    dependsOn(testExperimental)
  }
}

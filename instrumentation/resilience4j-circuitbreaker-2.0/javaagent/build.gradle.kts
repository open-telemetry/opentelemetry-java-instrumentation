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

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  library("io.github.resilience4j:resilience4j-circuitbreaker:2.0.0")
  latestDepTestLibrary("io.github.resilience4j:resilience4j-circuitbreaker:latest.release")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  test {
    jvmArgs("-Dotel.instrumentation.resilience4j-circuitbreaker.enabled=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.resilience4j-circuitbreaker.enabled=true"
    )
    filter {
      excludeTestsMatching("Resilience4jCircuitBreakerDisabledTest")
    }
  }

  val testDefaultDisabled = register<Test>("testDefaultDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("Resilience4jCircuitBreakerDisabledTest")
    }
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs(
      "-Dotel.instrumentation.resilience4j-circuitbreaker.enabled=true",
      "-Dotel.instrumentation.resilience4j-circuitbreaker.experimental-span-attributes=true"
    )
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.resilience4j-circuitbreaker.enabled=true,otel.instrumentation.resilience4j-circuitbreaker.experimental-span-attributes=true"
    )
    filter {
      excludeTestsMatching("Resilience4jCircuitBreakerDisabledTest")
    }
  }

  check {
    dependsOn(testDefaultDisabled, testExperimental)
  }
}

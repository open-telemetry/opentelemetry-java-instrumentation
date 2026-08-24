plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("net.spy")
    module.set("spymemcached")
    versions.set("[2.12.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("net.spy:spymemcached:2.12.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation("com.google.guava:guava")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  test {
    filter {
      excludeTestsMatching("*.testSanitizationDisabled")
      // getTimeout/setCancel rely on the operation staying queued (and never dispatched) long
      // enough for spymemcached to mark it timed out/cancelled; this is inherently sensitive to
      // scheduling and I/O timing and is not reliably reproducible in CI.
      excludeTestsMatching("*.getTimeout")
      excludeTestsMatching("*.setCancel")
    }
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      excludeTestsMatching("*.testSanitizationDisabled")
      // getTimeout/setCancel rely on the operation staying queued (and never dispatched) long
      // enough for spymemcached to mark it timed out/cancelled; this is inherently sensitive to
      // scheduling and I/O timing and is not reliably reproducible in CI.
      excludeTestsMatching("*.getTimeout")
      excludeTestsMatching("*.setCancel")
    }

    jvmArgs("-Dotel.instrumentation.spymemcached.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.spymemcached.experimental-span-attributes=true")
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      excludeTestsMatching("*.testSanitizationDisabled")
      // getTimeout/setCancel rely on the operation staying queued (and never dispatched) long
      // enough for spymemcached to mark it timed out/cancelled; this is inherently sensitive to
      // scheduling and I/O timing and is not reliably reproducible in CI.
      excludeTestsMatching("*.getTimeout")
      excludeTestsMatching("*.setCancel")
    }

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  val testSanitizationDisabled = register<Test>("testSanitizationDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("*.testSanitizationDisabled")
    }

    jvmArgs("-Dotel.instrumentation.spymemcached.query-sanitization.enabled=false")
    systemProperty("metadataConfig", "otel.instrumentation.spymemcached.query-sanitization.enabled=false")
  }

  check {
    dependsOn(testStableSemconv, testExperimental, testSanitizationDisabled)
  }
}

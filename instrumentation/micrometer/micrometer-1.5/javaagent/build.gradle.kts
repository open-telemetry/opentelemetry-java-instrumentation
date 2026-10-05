plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.micrometer")
    module.set("micrometer-core")
    versions.set("[1.5.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.micrometer:micrometer-core:1.5.0")

  bootstrap(project(":instrumentation:runtime-telemetry:bootstrap"))
  implementation(project(":instrumentation:micrometer:micrometer-1.5:library"))

  testImplementation(project(":instrumentation:micrometer:micrometer-1.5:testing"))

  // provides the JMX metrics that jvm-metrics-ownership defers to
  testInstrumentation(project(":instrumentation:runtime-telemetry:javaagent"))
}

tasks {
  val ownershipTests = listOf("testJvmMetricsOwnership", "testJvmMetricsOwnershipV3Preview").map { taskName ->
    register<Test>(taskName) {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      filter {
        includeTestsMatching("*JvmMetricsOwnershipEnabledTest")
      }
      include("**/*JvmMetricsOwnershipEnabledTest.*")
      jvmArgs(
        "-Dotel.instrumentation.micrometer.experimental.jvm-metrics-ownership.enabled=true",
        "-Dotel.instrumentation.micrometer.experimental.jvm-metrics-ownership.kept=jvm.classes.unloaded",
        "-Dotel.instrumentation.micrometer.experimental.histogram-gauges.enabled=true",
      )
      if (taskName.endsWith("V3Preview")) {
        jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
      }
    }
  }

  val testJvmMetricsOwnershipJfr = register<Test>("testJvmMetricsOwnershipJfr") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*JvmMetricsOwnershipJfrTest")
    }
    include("**/*JvmMetricsOwnershipJfrTest.*")
    jvmArgs(
      "-Dotel.instrumentation.micrometer.experimental.jvm-metrics-ownership.enabled=true",
      "-Dotel.instrumentation.runtime-telemetry.experimental.jfr-metrics.included=jvm.class.*",
    )
    enabled = (otelProps.testJavaVersion ?: JavaVersion.current()).isCompatibleWith(JavaVersion.VERSION_17)
  }

  val testPrometheusMode = register<Test>("testPrometheusMode") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*PrometheusModeTest")
    }
    include("**/*PrometheusModeTest.*")
    jvmArgs("-Dotel.instrumentation.micrometer.prometheus-mode.enabled=true")
  }

  val testBaseTimeUnit = register<Test>("testBaseTimeUnit") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*TimerMillisecondsTest")
    }
    include("**/*TimerMillisecondsTest.*")
    jvmArgs("-Dotel.instrumentation.micrometer.base-time-unit=milliseconds")
  }

  val testHistogramGauges = register<Test>("testHistogramGauges") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*HistogramGaugesTest")
    }
    include("**/*HistogramGaugesTest.*")
    jvmArgs("-Dotel.instrumentation.micrometer.experimental.histogram-gauges.enabled=true")
  }

  val testDeprecatedHistogramGauges = register<Test>("testDeprecatedHistogramGauges") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*HistogramGaugesTest")
    }
    include("**/*HistogramGaugesTest.*")
    jvmArgs("-Dotel.instrumentation.micrometer.histogram-gauges.enabled=true")
  }

  val testDeprecatedHistogramGaugesV3Preview =
    register<Test>("testDeprecatedHistogramGaugesV3Preview") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      filter {
        includeTestsMatching("*DistributionSummaryTest")
      }
      include("**/DistributionSummaryTest.*")
      jvmArgs(
        "-Dotel.instrumentation.micrometer.histogram-gauges.enabled=true",
        "-Dotel.instrumentation.common.v3-preview=true",
      )
    }

  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      excludeTestsMatching("*TimerMillisecondsTest")
      excludeTestsMatching("*PrometheusModeTest")
      excludeTestsMatching("*HistogramGaugesTest")
      excludeTestsMatching("*JvmMetricsOwnershipEnabledTest")
      excludeTestsMatching("*JvmMetricsOwnershipJfrTest")
    }
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
  }

  test {
    filter {
      excludeTestsMatching("*TimerMillisecondsTest")
      excludeTestsMatching("*PrometheusModeTest")
      excludeTestsMatching("*HistogramGaugesTest")
      excludeTestsMatching("*JvmMetricsOwnershipEnabledTest")
      excludeTestsMatching("*JvmMetricsOwnershipJfrTest")
    }
  }

  check {
    dependsOn(
      ownershipTests,
      testJvmMetricsOwnershipJfr,
      testBaseTimeUnit,
      testPrometheusMode,
      testHistogramGauges,
      testDeprecatedHistogramGauges,
      testDeprecatedHistogramGaugesV3Preview,
      testV3Preview,
    )
  }

  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.micrometer.enabled=true")
  }
}

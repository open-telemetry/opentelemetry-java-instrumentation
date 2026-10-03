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
  val testJvmMetricsOwnership = register<Test>("testJvmMetricsOwnership") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*JvmMetricsOwnershipEnabledTest")
    }
    include("**/*JvmMetricsOwnershipEnabledTest.*")
    jvmArgs(
      "-Dotel.instrumentation.micrometer.experimental.jvm-metrics-ownership.enabled=true",
      "-Dotel.instrumentation.micrometer.experimental.jvm-metrics-ownership.kept=jvm.classes.unloaded",
    )
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
    }
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
  }

  test {
    filter {
      excludeTestsMatching("*TimerMillisecondsTest")
      excludeTestsMatching("*PrometheusModeTest")
      excludeTestsMatching("*HistogramGaugesTest")
      excludeTestsMatching("*JvmMetricsOwnershipEnabledTest")
    }
  }

  check {
    dependsOn(
      testJvmMetricsOwnership,
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

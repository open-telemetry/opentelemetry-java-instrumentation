import io.opentelemetry.instrumentation.gradle.executions

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

testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation("io.micrometer:micrometer-core:1.5.0")
      }
    }
  }
}

testing.suites.executions("test") {
  configureEach {
    jvmArgs("-Dotel.instrumentation.micrometer.enabled=true")
  }

  configureDefault {
    filter {
      excludeTestsMatching("*TimerMillisecondsTest")
      excludeTestsMatching("*PrometheusModeTest")
      excludeTestsMatching("*HistogramGaugesTest")
      excludeTestsMatching("*JvmMetricsOwnershipEnabledTest")
      excludeTestsMatching("*JvmMetricsOwnershipJfrTest")
    }
  }

  register("JvmMetricsOwnership") {
    filter {
      includeTestsMatching("*JvmMetricsOwnershipEnabledTest")
    }
    include("**/*JvmMetricsOwnershipEnabledTest.*")
    jvmArgs(
      "-Dotel.instrumentation.micrometer.experimental.jvm-metrics-ownership.enabled=true",
      "-Dotel.instrumentation.micrometer.experimental.jvm-metrics-ownership.kept=jvm.classes.unloaded",
      "-Dotel.instrumentation.micrometer.experimental.histogram-gauges.enabled=true",
    )
  }

  register("JvmMetricsOwnershipJfr") {
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

  register("PrometheusMode") {
    filter {
      includeTestsMatching("*PrometheusModeTest")
    }
    include("**/*PrometheusModeTest.*")
    jvmArgs("-Dotel.instrumentation.micrometer.prometheus-mode.enabled=true")
  }

  register("BaseTimeUnit") {
    filter {
      includeTestsMatching("*TimerMillisecondsTest")
    }
    include("**/*TimerMillisecondsTest.*")
    jvmArgs("-Dotel.instrumentation.micrometer.base-time-unit=milliseconds")
  }

  register("HistogramGauges") {
    filter {
      includeTestsMatching("*HistogramGaugesTest")
    }
    include("**/*HistogramGaugesTest.*")
    jvmArgs("-Dotel.instrumentation.micrometer.experimental.histogram-gauges.enabled=true")
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}

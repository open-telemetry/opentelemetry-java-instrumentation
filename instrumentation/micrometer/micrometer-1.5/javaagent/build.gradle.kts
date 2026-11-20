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

  implementation(project(":instrumentation:micrometer:micrometer-1.5:library"))

  testImplementation(project(":instrumentation:micrometer:micrometer-1.5:testing"))

  // provides the JMX metrics that jvm-metrics-deduplication defers to
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

testing.suites {
  executions("test") {
    configureEach {
      jvmArgs("-Dotel.instrumentation.micrometer.enabled=true")
    }

    configureDefault {
      filter {
        excludeTestsMatching("*TimerMillisecondsTest")
        excludeTestsMatching("*PrometheusModeTest")
        excludeTestsMatching("*HistogramGaugesTest")
        excludeTestsMatching("*JvmMetricsDeduplicationDefaultTest")
        excludeTestsMatching("*JvmMetricsDeduplicationDisabledTest")
      }
    }

    register("JvmMetricsDeduplicationDefault") {
      filter {
        includeTestsMatching("*JvmMetricsDeduplicationDefaultTest")
      }
      include("**/*JvmMetricsDeduplicationDefaultTest.*")
      jvmArgs(
        "-Dotel.instrumentation.micrometer.experimental.histogram-gauges.enabled=true",
      )
    }

    register("JvmMetricsDeduplicationDisabled") {
      filter {
        includeTestsMatching("*JvmMetricsDeduplicationDisabledTest")
      }
      include("**/*JvmMetricsDeduplicationDisabledTest.*")
      jvmArgs("-Dotel.instrumentation.micrometer.experimental.jvm-metrics-deduplication.enabled=false")
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
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}

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
}

testing.suites.named<JvmTestSuite>("test") {
  executions {
    configureEach {
      jvmArgs("-Dotel.instrumentation.micrometer.enabled=true")
    }

    configureDefault {
      filter {
        excludeTestsMatching("*TimerMillisecondsTest")
        excludeTestsMatching("*PrometheusModeTest")
        excludeTestsMatching("*HistogramGaugesTest")
      }
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

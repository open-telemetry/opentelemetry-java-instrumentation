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

testing {
  suites {
    val testPrometheusMode by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            filter {
              includeTestsMatching("*PrometheusModeTest")
            }
            include("**/*PrometheusModeTest.*")
            jvmArgs("-Dotel.instrumentation.micrometer.prometheus-mode.enabled=true")
          }
        }
      }
    }

    val testBaseTimeUnit by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            filter {
              includeTestsMatching("*TimerMillisecondsTest")
            }
            include("**/*TimerMillisecondsTest.*")
            jvmArgs("-Dotel.instrumentation.micrometer.base-time-unit=milliseconds")
          }
        }
      }
    }

    val testHistogramGauges by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            filter {
              includeTestsMatching("*HistogramGaugesTest")
            }
            include("**/*HistogramGaugesTest.*")
            jvmArgs("-Dotel.instrumentation.micrometer.histogram-gauges.enabled=true")
          }
        }
      }
    }
  }
}

tasks {
  test {
    filter {
      excludeTestsMatching("*TimerMillisecondsTest")
      excludeTestsMatching("*PrometheusModeTest")
      excludeTestsMatching("*HistogramGaugesTest")
    }
  }

  check {
    dependsOn(testing.suites.named("testBaseTimeUnit"))
    dependsOn(testing.suites.named("testPrometheusMode"))
    dependsOn(testing.suites.named("testHistogramGauges"))
  }

  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.micrometer.enabled=true")
  }
}

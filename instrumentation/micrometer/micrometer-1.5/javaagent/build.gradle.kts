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

tasks {
  val testPrometheusMode by registering(Test::class) {
    filter {
      includeTestsMatching("*PrometheusModeTest")
    }
    include("**/*PrometheusModeTest.*")
    jvmArgs("-Dotel.instrumentation.micrometer.prometheus-mode.enabled=true")
  }

  val testBaseTimeUnit by registering(Test::class) {
    filter {
      includeTestsMatching("*TimerMillisecondsTest")
    }
    include("**/*TimerMillisecondsTest.*")
    jvmArgs("-Dotel.instrumentation.micrometer.base-time-unit=milliseconds")
  }

  val testHistogramGauges by registering(Test::class) {
    filter {
      includeTestsMatching("*HistogramGaugesTest")
    }
    include("**/*HistogramGaugesTest.*")
    jvmArgs("-Dotel.instrumentation.micrometer.histogram-gauges.enabled=true")
  }

  test {
    filter {
      excludeTestsMatching("*TimerMillisecondsTest")
      excludeTestsMatching("*PrometheusModeTest")
      excludeTestsMatching("*HistogramGaugesTest")
    }
  }

  check {
    dependsOn(testBaseTimeUnit)
    dependsOn(testPrometheusMode)
    dependsOn(testHistogramGauges)
  }

  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.micrometer.enabled=true")
  }
}

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
  val testBaseTimeUnit by registering(Test::class) {
    filter {
      includeTestsMatching("*TimerSecondsTest")
      includeTestsMatching("*LongTimerSecondsTest")
      isFailOnNoMatchingTests = false
    }
    include("**/*TimerSecondsTest.*", "**/*LongTaskTimerSecondsTest.*")
    jvmArgs("-Dotel.instrumentation.micrometer.base-time-unit=seconds")
  }

  test {
    dependsOn(testBaseTimeUnit)
    filter {
      excludeTestsMatching("*TimerSecondsTest")
      excludeTestsMatching("*LongTimerSecondsTest")
      isFailOnNoMatchingTests = false
    }
  }
}

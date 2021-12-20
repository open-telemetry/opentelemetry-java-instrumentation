plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("ch.qos.logback:logback-classic:1.0.0")

  testImplementation(project(":instrumentation:logback:logback-mdc-1.0:testing"))

  // 1.3+ contains breaking changes, check back after it stabilizes.
  latestDepTestLibrary("ch.qos.logback:logback-classic:1.2.+")
}

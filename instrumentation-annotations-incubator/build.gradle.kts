plugins {
  id("otel.java-conventions")
  id("otel.japicmp-conventions")
  id("otel.publish-conventions")

  id("otel.animalsniffer-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencies {
  api("io.opentelemetry:opentelemetry-api")
}

tasks.test {
  // This module does not have tests, but has example classes in the test directory. Gradle 9 fails
  // the build when there are source files in the test directory but no tests to run so we disable
  // the test task.
  enabled = false
}

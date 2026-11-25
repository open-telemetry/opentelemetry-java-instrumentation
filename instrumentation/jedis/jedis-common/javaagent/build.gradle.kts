import io.opentelemetry.instrumentation.gradle.unitTests

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

testing.suites {
  unitTests()
}

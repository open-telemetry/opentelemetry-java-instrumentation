plugins {
  id("io.opentelemetry.instrumentation.base")
}

dependencies {
  api("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api")
  api("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-semconv")

  api("io.opentelemetry:opentelemetry-api")

  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
}

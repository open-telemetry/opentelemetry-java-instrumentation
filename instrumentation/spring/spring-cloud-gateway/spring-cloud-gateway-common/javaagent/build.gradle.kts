plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-api")
  compileOnly("io.opentelemetry:opentelemetry-api-incubator")
}

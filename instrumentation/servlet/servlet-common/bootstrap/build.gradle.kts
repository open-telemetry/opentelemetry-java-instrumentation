plugins {
  id("otel.javaagent-bootstrap")
}

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-api-incubator")
}

plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("org.apache.kafka:kafka-clients:0.11.0.0")
}

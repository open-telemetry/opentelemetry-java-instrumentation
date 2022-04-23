plugins {
  id("otel.javaagent-instrumentation")
}


dependencies {
  library("org.apache.pulsar:pulsar-client:2.9.0")
}

plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  library("io.micrometer:micrometer-core:1.5.0")
}

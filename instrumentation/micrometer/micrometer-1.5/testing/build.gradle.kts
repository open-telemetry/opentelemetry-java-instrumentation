plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")

  api("io.micrometer:micrometer-core:1.5.0")
}

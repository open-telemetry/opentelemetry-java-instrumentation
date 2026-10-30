plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("io.opentelemetry.javaagent:opentelemetry-instrumentation-testing")
  implementation("org.apache.wicket:wicket:8.0.0")
  implementation("org.jsoup:jsoup:1.13.1")
}

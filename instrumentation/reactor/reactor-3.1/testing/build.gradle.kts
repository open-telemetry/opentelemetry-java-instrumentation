plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing")

  api("io.projectreactor:reactor-core:3.1.0.RELEASE")

  implementation("io.opentelemetry:opentelemetry-api")
}

plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-instrumentation-testing")

  compileOnly(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
  compileOnly("io.projectreactor.kafka:reactor-kafka:1.0.0.RELEASE")
  implementation("org.testcontainers:testcontainers-kafka")
}

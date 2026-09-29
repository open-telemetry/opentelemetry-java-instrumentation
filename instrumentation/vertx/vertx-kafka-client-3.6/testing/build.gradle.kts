plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  implementation("org.testcontainers:testcontainers-kafka")

  compileOnly(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
  compileOnly("io.vertx:vertx-kafka-client:3.6.0")
  // vertx-codegen is needed for Xlint's annotation checking
  compileOnly("io.vertx:vertx-codegen:3.6.0")
}

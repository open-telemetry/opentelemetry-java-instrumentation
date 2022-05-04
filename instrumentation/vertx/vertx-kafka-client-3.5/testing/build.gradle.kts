plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation(project(":testing-common"))
  implementation("org.testcontainers:kafka")

  compileOnly("io.vertx:vertx-kafka-client:3.5.0")
  // vertx-codegen and vertx-docgen dependencies are needed for Xlint's annotation checking
  compileOnly("io.vertx:vertx-codegen:3.0.0")
  compileOnly("io.vertx:vertx-docgen:3.0.0")
}

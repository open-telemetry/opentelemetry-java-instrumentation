plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")

  // each test suite brings its own vertx-core version
  compileOnly("io.vertx:vertx-core:3.0.0")
  // vertx-codegen / vertx-docgen are needed for Xlint's annotation checking
  compileOnly("io.vertx:vertx-codegen:3.0.0")
  compileOnly("io.vertx:vertx-docgen:3.0.0")
}

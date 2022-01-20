plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("io.vertx:vertx-core:3.0.0")
  compileOnly("io.vertx:vertx-codegen:3.0.0")
  compileOnly("io.vertx:vertx-docgen:3.0.0")
}

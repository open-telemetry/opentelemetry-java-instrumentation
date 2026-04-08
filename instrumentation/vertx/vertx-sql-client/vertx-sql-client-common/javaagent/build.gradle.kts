plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("io.vertx:vertx-sql-client:4.0.0")
  compileOnly("io.vertx:vertx-codegen:4.0.0")
}

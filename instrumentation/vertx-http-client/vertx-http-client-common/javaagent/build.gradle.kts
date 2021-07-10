plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("io.vertx:vertx-core:3.0.0")
}

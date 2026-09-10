plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:vertx:vertx-redis-client:vertx-redis-client-4.4.5:javaagent"))
  testImplementation("io.vertx:vertx-redis-client:4.4.5")
}

plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(
    project(
      ":instrumentation:vertx:vertx-sql-client:vertx-sql-client-common-4.0:javaagent",
    ),
  )
  testImplementation("io.vertx:vertx-sql-client:4.0.0")
}

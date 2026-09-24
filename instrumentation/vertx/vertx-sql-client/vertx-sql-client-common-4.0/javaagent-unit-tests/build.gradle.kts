plugins {
  id("otel.java-conventions")
}

tasks {
  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(
    project(
      ":instrumentation:vertx:vertx-sql-client:vertx-sql-client-common-4.0:javaagent",
    ),
  )
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("io.vertx:vertx-sql-client:4.0.0")
}

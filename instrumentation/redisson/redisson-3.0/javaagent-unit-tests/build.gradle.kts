plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:redisson:redisson-3.0:javaagent"))
  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  testImplementation(
    "org.redisson:redisson:${if (otelProps.testLatestDeps) "3.16.+" else "3.0.0"}",
  )
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }
}

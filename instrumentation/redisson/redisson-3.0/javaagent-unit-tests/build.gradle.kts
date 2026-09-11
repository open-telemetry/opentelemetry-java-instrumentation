plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:redisson:redisson-3.0:javaagent"))
  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  testImplementation(
    "org.redisson:redisson:${baseVersion("3.0.0").orLatest("3.16.+")}",
  )
}

tasks.test {
  systemProperty("testLatestDeps", otelProps.testLatestDeps)
}

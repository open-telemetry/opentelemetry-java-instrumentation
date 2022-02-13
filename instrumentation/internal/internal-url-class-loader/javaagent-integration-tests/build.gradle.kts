plugins {
  id("otel.javaagent-instrumentation")
  id("otel.javaagent-testing")
}

dependencies {
  testImplementation("org.apache.commons:commons-lang3:3.12.0")
  testImplementation("commons-io:commons-io:2.8.0")

  testInstrumentation(project(":instrumentation:internal:internal-url-class-loader:javaagent"))
}

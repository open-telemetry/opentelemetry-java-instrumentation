plugins {
  id("otel.javaagent-instrumentation")
  id("otel.javaagent-testing")
}

dependencies {
  compileOnly("org.apache.commons:commons-lang3:3.12.0")
  testImplementation("org.apache.commons:commons-lang3:3.12.0")

  testInstrumentation(project(":instrumentation:internal:internal-class-loader:javaagent"))
}

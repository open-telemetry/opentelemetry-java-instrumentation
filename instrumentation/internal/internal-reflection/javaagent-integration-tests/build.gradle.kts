plugins {
  id("otel.javaagent-instrumentation")
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:internal:internal-reflection:javaagent"))
  testCompileOnly(project(":javaagent-bootstrap"))
}

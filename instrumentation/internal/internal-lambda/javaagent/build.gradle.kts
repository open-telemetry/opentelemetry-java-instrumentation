plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  testImplementation(project(":javaagent-bootstrap"))
}

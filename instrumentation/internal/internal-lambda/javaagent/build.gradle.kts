plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  testImplementation(project(":javaagent-bootstrap"))
}

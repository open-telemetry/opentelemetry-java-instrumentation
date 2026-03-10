plugins {
  id("otel.javaagent-bootstrap")
}

dependencies {
  compileOnly(project(":instrumentation-api-incubator"))
}

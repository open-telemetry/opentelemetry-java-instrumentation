plugins {
  id("otel.javaagent-bootstrap")
}

dependencies {
  compileOnly(project(":instrumentation-api-incubator"))
  testImplementation(project(":javaagent-extension-api"))
}

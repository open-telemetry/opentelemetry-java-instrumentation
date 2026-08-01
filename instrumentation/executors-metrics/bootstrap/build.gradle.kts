plugins {
  id("otel.javaagent-bootstrap")
}

dependencies {
  testImplementation(project(":javaagent-extension-api"))
}

plugins {
  id("otel.javaagent-bootstrap")
}

dependencies {
  compileOnly(project(":javaagent-bootstrap"))
}

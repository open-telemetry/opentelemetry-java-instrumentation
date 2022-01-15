plugins {
  id("otel.javaagent-bootstrap")
}

dependencies {
  api(project(":instrumentation:servlet:servlet-common:bootstrap"))
}

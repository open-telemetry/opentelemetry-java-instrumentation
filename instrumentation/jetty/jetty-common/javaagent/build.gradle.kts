plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  api(project(":instrumentation:servlet:servlet-common:javaagent"))
}

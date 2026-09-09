plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  api(project(":instrumentation:servlet:servlet-common:javaagent"))
}

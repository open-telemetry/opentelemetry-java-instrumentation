plugins {
  id("otel.javaagent-shadow-conventions")
}

dependencies {
  bootstrapLibs(project(":bootstrap"))

  javaagentLibs(project(":custom"))
  javaagentLibs(project(":instrumentation:servlet-3"))

  upstreamAgent("io.opentelemetry.javaagent:opentelemetry-javaagent:$opentelemetryJavaagentVersion")
}

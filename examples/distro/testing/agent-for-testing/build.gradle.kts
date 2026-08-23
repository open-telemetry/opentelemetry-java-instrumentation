plugins {
  id("otel.javaagent-shadow-conventions")
}

dependencies {
  bootstrapLibs(project(":bootstrap"))
  // and finally include everything from otel agent for testing
  upstreamAgent("io.opentelemetry.javaagent:opentelemetry-agent-for-testing:$opentelemetryJavaagentAlphaVersion")
}

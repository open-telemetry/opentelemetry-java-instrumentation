plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")
  compileOnly("com.openai:openai-java:3.0.0")

  if (otelProps.testLatestDeps) {
    runtimeOnly("com.openai:openai-java:3.0.0")
  }
}

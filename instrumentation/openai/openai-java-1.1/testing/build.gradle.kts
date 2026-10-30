plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-instrumentation-testing")
  api("com.openai:openai-java:1.1.0")
  api(project(":instrumentation:openai:openai-java-1.1:openai3-testing"))
}

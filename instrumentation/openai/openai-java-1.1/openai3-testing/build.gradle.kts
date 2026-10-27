plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.instrumentation:opentelemetry-instrumentation-testing")
  compileOnly("com.openai:openai-java:3.0.0")
}

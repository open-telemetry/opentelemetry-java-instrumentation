plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.instrumentation:opentelemetry-instrumentation-testing")
  api("com.graphql-java:graphql-java:12.0")
}

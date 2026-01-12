plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  implementation("com.graphql-java:graphql-java:12.0")
}

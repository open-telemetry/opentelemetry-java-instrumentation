plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:graphql-java:graphql-java-common:library"))

  library("com.graphql-java:graphql-java:12.0")
}

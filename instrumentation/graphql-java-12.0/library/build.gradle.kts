plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("com.graphql-java:graphql-java:12.0")

  testImplementation(project(":instrumentation:graphql-java-12.0:testing"))
}

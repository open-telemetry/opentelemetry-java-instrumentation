plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("com.graphql-java:graphql-java:12.0")
  implementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:library"))

  testImplementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:testing"))

  latestDepTestLibrary("com.graphql-java:graphql-java:19.+") // see graphql-java-20.0 module
}

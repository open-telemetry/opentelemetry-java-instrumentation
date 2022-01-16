plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("com.graphql-java:graphql-java:17.0")
//  implementation(project(":instrumentation-api-annotation-support"))


  testImplementation(project(":instrumentation:graphql-java:graphql-java-17:testing"))

  latestDepTestLibrary("com.graphql-java:graphql-java:17.3")
}

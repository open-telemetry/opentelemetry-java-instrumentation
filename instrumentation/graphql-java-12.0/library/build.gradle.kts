plugins {
  id("otel.library-instrumentation")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.graphql.v12_0")

dependencies {
  library("com.graphql-java:graphql-java:12.0")

  testImplementation(project(":instrumentation:graphql-java-12.0:testing"))
}

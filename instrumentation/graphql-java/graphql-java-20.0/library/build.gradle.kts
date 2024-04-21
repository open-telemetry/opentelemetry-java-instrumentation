plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("com.graphql-java:graphql-java:20.0")

  testImplementation(project(":instrumentation:graphql-java:graphql-java-common:testing"))
}

if (findProperty("testLatestDeps") as Boolean) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}

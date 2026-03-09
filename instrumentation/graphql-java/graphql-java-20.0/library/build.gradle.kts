plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("com.graphql-java:graphql-java:20.0")
  implementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:library"))

  testImplementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:testing"))
}

if (findProperty("testLatestDeps") as Boolean) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.graphql.data-fetcher.enabled=true")
}

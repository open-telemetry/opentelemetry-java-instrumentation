plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("com.graphql-java:graphql-java:12.0")
  implementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:library"))

  testImplementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:testing"))

  latestDepTestLibrary("com.graphql-java:graphql-java:19.+") // see graphql-java-20.0 module
}

val testExceptionLogs by tasks.registering(Test::class) {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
  systemProperty(
    "metadataConfig",
    "otel.instrumentation.graphql.operation-name-in-span-name.enabled=true,otel.semconv.exception.signal.preview=logs",
  )
  filter {
    includeTestsMatching("*.GraphqlTest.parseError")
    includeTestsMatching("*.GraphqlTest.validationError")
    includeTestsMatching("*.GraphqlTest.dataFetcherException")
    includeTestsMatching("*.GraphqlTest.dataFetcherError")
  }
}

tasks.check {
  dependsOn(testExceptionLogs)
}

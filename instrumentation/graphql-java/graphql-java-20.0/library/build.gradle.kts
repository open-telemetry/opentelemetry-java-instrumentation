plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("com.graphql-java:graphql-java:20.0")
  implementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:library"))

  testImplementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:testing"))
}

if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}

tasks.test {
  jvmArgs("-Dotel.instrumentation.graphql.data-fetcher.enabled=true")
}

val testExceptionLogs by tasks.registering(Test::class) {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs(
    "-Dotel.instrumentation.graphql.data-fetcher.enabled=true",
    "-Dotel.semconv.exception.signal.preview=logs",
  )
  systemProperty(
    "metadataConfig",
    "otel.instrumentation.graphql.data-fetcher.enabled=true,otel.instrumentation.graphql.operation-name-in-span-name.enabled=true,otel.semconv.exception.signal.preview=logs",
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

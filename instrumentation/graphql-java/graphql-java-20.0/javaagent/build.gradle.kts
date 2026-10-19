plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("com.graphql-java")
    module.set("graphql-java")
    versions.set("[20,)")
    skip("230521-nf-execution")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:graphql-java:graphql-java-20.0:library"))
  implementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:library"))

  library("com.graphql-java:graphql-java:20.0")

  testInstrumentation(project(":instrumentation:graphql-java:graphql-java-12.0:javaagent"))

  testImplementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:testing"))
}

tasks {
  withType<Test>().configureEach {
    jvmArgs(
      "-Dotel.instrumentation.graphql.operation-name-in-span-name.enabled=true",
    )
  }

  val testDataFetcher = register<Test>("testDataFetcher") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.graphql.data-fetcher.enabled=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.graphql.data-fetcher.enabled=true,otel.instrumentation.graphql.operation-name-in-span-name.enabled=true",
    )
  }

  val testExceptionLogs = register<Test>("testExceptionLogs") {
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

  val testDataFetcherExceptionLogs = register<Test>("testDataFetcherExceptionLogs") {
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
      includeTestsMatching("*.GraphqlTest.dataFetcherException")
      includeTestsMatching("*.GraphqlTest.dataFetcherError")
    }
  }

  check {
    dependsOn(testDataFetcher, testExceptionLogs, testDataFetcherExceptionLogs)
  }
}

if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}

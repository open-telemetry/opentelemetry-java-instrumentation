import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("com.graphql-java:graphql-java:12.0")
  implementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:library"))

  testImplementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:testing"))

  latestDepTestLibrary("com.graphql-java:graphql-java:19.+") // see graphql-java-20.0 module
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("ExceptionLogs") {
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
      }
    }
  }
}

import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

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

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          jvmArgs("-Dotel.instrumentation.graphql.operation-name-in-span-name.enabled=true")
        }
        register("DataFetcher") {
          telemetryConfig("otel.instrumentation.graphql.data-fetcher.enabled", "true")
          telemetryConfig("otel.instrumentation.graphql.operation-name-in-span-name.enabled", "true")
        }
        register("ExceptionLogs") {
          telemetryConfig("otel.instrumentation.graphql.operation-name-in-span-name.enabled", "true")
          telemetryConfig("otel.semconv.exception.signal.preview", "logs")
          filter {
            includeTestsMatching("*.GraphqlTest.parseError")
            includeTestsMatching("*.GraphqlTest.validationError")
            includeTestsMatching("*.GraphqlTest.dataFetcherException")
            includeTestsMatching("*.GraphqlTest.dataFetcherError")
          }
        }
        register("DataFetcherExceptionLogs") {
          telemetryConfig("otel.instrumentation.graphql.data-fetcher.enabled", "true")
          telemetryConfig("otel.instrumentation.graphql.operation-name-in-span-name.enabled", "true")
          telemetryConfig("otel.semconv.exception.signal.preview", "logs")
          filter {
            includeTestsMatching("*.GraphqlTest.dataFetcherException")
            includeTestsMatching("*.GraphqlTest.dataFetcherError")
          }
        }
      }
    }
  }
}

if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}

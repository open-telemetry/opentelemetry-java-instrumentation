import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")

  library("com.amazonaws:aws-java-sdk-core:1.11.0")
  library("com.amazonaws:aws-java-sdk-sqs:1.11.106")
  compileOnly(project(":muzzle"))

  testImplementation(project(":instrumentation:aws-sdk:aws-sdk-1.11:testing"))

  testLibrary("com.amazonaws:aws-java-sdk-dynamodb:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-ec2:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-kinesis:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-lambda:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-rds:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-s3:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-sns:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-stepfunctions:1.11.106")
}

if (!otelProps.testLatestDeps) {
  configurations.testRuntimeClasspath {
    resolutionStrategy {
      eachDependency {
        // early versions of aws sdk are not compatible with jackson 2.16.0
        if (requested.group.startsWith("com.fasterxml.jackson")) {
          useVersion("2.15.3")
        }
      }
    }
  }
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("ExperimentalAttributes") {
          systemProperty("test.captureExperimentalSpanAttributes", "true")
          systemProperty(
            "metadataConfig",
            "AwsSdkTelemetryBuilder.setCaptureExperimentalSpanAttributes(true)",
          )
          filter {
            excludeTestsMatching("SqsSuppressReceiveSpansTest")
            excludeTestsMatching("SqsTracingTest.testProcessingOwnershipDisablesRawProcessSpansForResponse")
            excludeTestsMatching("SqsTracingTest.testDisableSqsMessageCreateSpans*")
            excludeTestsMatching("SqsTracingTest.testNoopTelemetryDoesNotInjectInvalidCreationContext")
            excludeTestsMatching("SqsTracingTest.testDisableSqsMessageCreateSpansPreservesCustomContext")
            excludeTestsMatching("SqsTracingTest.testBatchLinksSurviveLaterRequestClone")
          }
        }
        register("SqsReceiveTelemetry") {
          systemProperty("test.captureExperimentalSpanAttributes", "true")
          systemProperty(
            "test.messagingReceiveTelemetryEnabled",
            "true",
          )
          systemProperty(
            "metadataConfig",
            "AwsSdkTelemetryBuilder.setCaptureExperimentalSpanAttributes(true)," +
              "AwsSdkTelemetryBuilder.setMessagingReceiveTelemetryEnabled(true)",
          )
          filter {
            includeTestsMatching("SqsTracingTest")
            excludeTestsMatching("SqsTracingTest.testDisableSqsMessageCreateSpans*")
            excludeTestsMatching("SqsTracingTest.testNoopTelemetryDoesNotInjectInvalidCreationContext")
            excludeTestsMatching("SqsTracingTest.testDisableSqsMessageCreateSpansPreservesCustomContext")
            excludeTestsMatching("SqsTracingTest.testBatchLinksSurviveLaterRequestClone")
          }
        }
        register("ExceptionSignalLogs") {
          jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
          systemProperty("test.captureExperimentalSpanAttributes", "true")
          systemProperty(
            "metadataConfig",
            "AwsSdkTelemetryBuilder.setCaptureExperimentalSpanAttributes(true)," +
              "otel.semconv.exception.signal.preview=logs",
          )
          filter {
            includeTestsMatching("S3ClientTest")
            includeTestsMatching("SqsTracingTest.testDeleteMessageError")
          }
        }
        register("PreviewSemconv") {
          filter.includeTestsMatching("*S3ClientTest.testServiceError")
          jvmArgs("-Dotel.semconv-stability.preview=rpc")
        }
        register("BothSemconv") {
          filter.includeTestsMatching("*S3ClientTest.testServiceError")
          jvmArgs("-Dotel.semconv-stability.preview=rpc/dup")
        }
      }
    }

    register<JvmTestSuite>("testSecretsManager") {
      dependencies {
        implementation(project())
        implementation(project(":instrumentation:aws-sdk:aws-sdk-1.11:testing"))
        val version = baseVersion("1.12.80").orLatest()
        implementation("com.amazonaws:aws-java-sdk-secretsmanager:$version")
      }
      executions {
        register("ExperimentalAttributes") {
          systemProperty("test.captureExperimentalSpanAttributes", "true")
          systemProperty(
            "metadataConfig",
            "AwsSdkTelemetryBuilder.setCaptureExperimentalSpanAttributes(true)",
          )
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  test {
    filter.excludeTestsMatching("SqsTracingTest.testProcessingOwnershipDisablesRawProcessSpansForResponse")
  }

  check {
    dependsOn(testing.suites)
  }
}

import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")

  library("software.amazon.awssdk:aws-core:2.2.0")
  library("software.amazon.awssdk:sqs:2.2.0")
  library("software.amazon.awssdk:lambda:2.2.0")
  library("software.amazon.awssdk:sns:2.2.0")
  library("software.amazon.awssdk:aws-json-protocol:2.2.0")
  // json-utils was added in 2.17.0
  compileOnly("software.amazon.awssdk:json-utils:2.17.0")
  compileOnly(project(":muzzle")) // For @NoMuzzle

  // Don't use library to make sure base test is run with the floor version.
  // bedrock runtime is tested separately in testBedrockRuntime.
  // First release with Converse API
  compileOnly("software.amazon.awssdk:bedrockruntime:2.25.63")

  testImplementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:testing"))

  testLibrary("software.amazon.awssdk:dynamodb:2.2.0")
  testLibrary("software.amazon.awssdk:ec2:2.2.0")
  testLibrary("software.amazon.awssdk:kinesis:2.2.0")
  testLibrary("software.amazon.awssdk:lambda:2.2.0")
  testLibrary("software.amazon.awssdk:rds:2.2.0")
  testLibrary("software.amazon.awssdk:s3:2.2.0")
  testLibrary("software.amazon.awssdk:secretsmanager:2.2.0")
  testLibrary("software.amazon.awssdk:ses:2.2.0")
  testLibrary("software.amazon.awssdk:sfn:2.2.0")
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
            excludeTestsMatching("Aws2SqsSuppressReceiveSpansTest")
            excludeTestsMatching("Aws2SqsW3cPropagatorTest")
            excludeTestsMatching("Aws2SqsW3cPropagatorAndXrayPropagatorTest")
            excludeTestsMatching("Aws2SqsSuppressReceiveSpansDefaultPropagatorTest")
            excludeTestsMatching("Aws2SqsSuppressReceiveSpansW3cPropagatorTest")
            excludeTestsMatching("Aws2SqsSuppressReceiveSpansW3cPropagatorAndXrayPropagatorTest")
            // Keep inherited SQS coverage by excluding only subclass-specific regression tests.
            excludeTestsMatching("Aws2SqsDefaultPropagatorTest.testDuplicateTracingInterceptor")
            excludeTestsMatching("Aws2SqsDefaultPropagatorTest.testDisableSqsMessageCreateSpans")
            excludeTestsMatching("Aws2SqsDefaultPropagatorTest.testNoopTelemetryDoesNotInjectInvalidCreationContext")
            excludeTestsMatching("Aws2ClientNotRecordHttpErrorTest")
            excludeTestsMatching("Aws2ClientRecordHttpErrorTest")
            excludeTestsMatching("SqsProcessMetricsIsolationTest")
          }
        }
        register("ReceiveTelemetry") {
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
            includeTestsMatching("Aws2SqsDefaultPropagatorTest")
            excludeTestsMatching("Aws2SqsDefaultPropagatorTest.testDuplicateTracingInterceptor")
            excludeTestsMatching("Aws2SqsDefaultPropagatorTest.testDisableSqsMessageCreateSpans")
            excludeTestsMatching("Aws2SqsDefaultPropagatorTest.testNoopTelemetryDoesNotInjectInvalidCreationContext")
          }
        }
        register("IndividualHttpErrors") {
          systemProperty("test.recordIndividualHttpError", "true")
          systemProperty(
            "metadataConfig",
            "AwsSdkTelemetryBuilder.setRecordIndividualHttpError(true)",
          )
          filter.includeTestsMatching("Aws2ClientRecordHttpErrorTest")
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
            includeTestsMatching("Aws2ClientTest")
            includeTestsMatching("Aws2SqsDefaultPropagatorTest.testDeleteMessageError")
          }
        }
      }
    }

    register<JvmTestSuite>("testCoreOnly") {
      dependencies {
        implementation(project())
        implementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:testing"))
        compileOnly("software.amazon.awssdk:sqs:2.2.0")
        val version = baseVersion("2.2.0").orLatest()
        implementation("software.amazon.awssdk:aws-core:$version")
        implementation("software.amazon.awssdk:aws-json-protocol:$version")
        implementation("software.amazon.awssdk:dynamodb:$version")
        implementation("software.amazon.awssdk:lambda:$version")
      }
      executions {
        register("ExperimentalAttributes") {
          systemProperty("test.captureExperimentalSpanAttributes", "true")
        }
      }
    }

    register<JvmTestSuite>("testLambda") {
      dependencies {
        implementation(project())
        implementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:testing"))
        val version = baseVersion("2.17.0").orLatest()
        implementation("software.amazon.awssdk:lambda:$version")
      }
    }

    register<JvmTestSuite>("testBedrockRuntime") {
      dependencies {
        implementation(project())
        implementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:testing"))
        compileOnly("software.amazon.awssdk:bedrockruntime:2.26.5")
        val version = baseVersion("2.25.63").orLatest()
        runtimeOnly("software.amazon.awssdk:bedrockruntime:$version")
      }
    }

    register<JvmTestSuite>("testRdsData") {
      dependencies {
        implementation(project())
        implementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:testing"))
        val version = baseVersion("2.5.54").orLatest()
        implementation("software.amazon.awssdk:rdsdata:$version")
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

  check {
    dependsOn(testing.suites)
  }
}

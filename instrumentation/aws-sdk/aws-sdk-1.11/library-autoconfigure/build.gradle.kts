import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.library-instrumentation")
}

base.archivesName.set("${base.archivesName.get()}-autoconfigure")

dependencies {
  implementation(project(":instrumentation:aws-sdk:aws-sdk-1.11:library"))

  library("com.amazonaws:aws-java-sdk-core:1.11.0")

  testImplementation(project(":instrumentation:aws-sdk:aws-sdk-1.11:testing"))

  testLibrary("com.amazonaws:aws-java-sdk-s3:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-rds:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-ec2:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-kinesis:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-dynamodb:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-sns:1.11.106")
  testLibrary("com.amazonaws:aws-java-sdk-sqs:1.11.106")
}

tasks {
  test {
    filter {
      excludeTestsMatching("SqsSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true")
  }
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          systemProperty("otel.instrumentation.aws-sdk.experimental-span-attributes", "true")
          systemProperty("otel.instrumentation.common.messaging.headers.included", "Test-Message-Header")
          systemProperty("testLatestDeps", otelProps.testLatestDeps)
        }
        register("ReceiveSpansDisabled") {
          filter {
            includeTestsMatching("SqsSuppressReceiveSpansTest")
          }
          include("**/SqsSuppressReceiveSpansTest.*")
        }
      }
    }
  }
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

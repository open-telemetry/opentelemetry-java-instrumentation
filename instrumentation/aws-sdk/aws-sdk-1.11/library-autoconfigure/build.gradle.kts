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

  // last version that does not use json protocol
  latestDepTestLibrary("com.amazonaws:aws-java-sdk-sqs:1.12.583") // documented limitation
}

tasks {
  withType<Test>().configureEach {
    systemProperty("otel.instrumentation.aws-sdk.experimental-span-attributes", "true")
    systemProperty("otel.instrumentation.messaging.experimental.capture-headers", "test-message-header")
  }

  val testReceiveSpansDisabled by registering(Test::class) {
    filter {
      includeTestsMatching("SqsSuppressReceiveSpansTest")
    }
    include("**/SqsSuppressReceiveSpansTest.*")
  }

  test {
    filter {
      excludeTestsMatching("SqsSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  check {
    dependsOn(testReceiveSpansDisabled)
  }
}

if (!(findProperty("testLatestDeps") as Boolean)) {
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

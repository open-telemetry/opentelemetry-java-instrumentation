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
    }
  }
}

tasks {
  val dynamoDbErrorModes = listOf("v0", "new", "dual").map { mode ->
    register<Test>("testDynamoDbErrors${mode.replaceFirstChar { it.uppercase() }}") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      filter.includeTestsMatching("*DynamoDbErrorsTest")
      systemProperty("test.dynamodb.rpc-semconv", mode)
    }
  }

  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  val testExceptionSignalLogs = register<Test>("testExceptionSignalLogs") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
  }

  check {
    dependsOn(
      testing.suites,
      testExceptionSignalLogs,
      dynamoDbErrorModes,
    )
  }
}

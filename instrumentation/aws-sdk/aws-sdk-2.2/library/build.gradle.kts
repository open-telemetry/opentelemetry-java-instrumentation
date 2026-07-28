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
        val version = baseVersion("2.25.63").orLatest()
        implementation("software.amazon.awssdk:bedrockruntime:$version")
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
  withType<Test>().configureEach {
    // NB: If you'd like to change these, there is some cleanup work to be done, as most tests ignore this and
    // set the value directly (the "library" does not normally query it, only library-autoconfigure)
    systemProperty("otel.instrumentation.aws-sdk.experimental-span-attributes", true)
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  val testCoreOnlyStableSemconv = register<Test>("testCoreOnlyStableSemconv") {
    val testCoreOnlySourceSet = sourceSets["testCoreOnly"]
    testClassesDirs = testCoreOnlySourceSet.output.classesDirs
    classpath = testCoreOnlySourceSet.runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  val testExceptionSignalLogs = register<Test>("testExceptionSignalLogs") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
  }

  val testRdsDataStableSemconv = register<Test>("testRdsDataStableSemconv") {
    val testRdsDataSourceSet = sourceSets["testRdsData"]
    testClassesDirs = testRdsDataSourceSet.output.classesDirs
    classpath = testRdsDataSourceSet.runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(
      testing.suites,
      testStableSemconv,
      testCoreOnlyStableSemconv,
      testExceptionSignalLogs,
      testRdsDataStableSemconv,
    )
  }
}

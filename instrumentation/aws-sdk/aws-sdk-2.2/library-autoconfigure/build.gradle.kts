plugins {
  id("otel.library-instrumentation")
}

base.archivesName.set("${base.archivesName.get()}-autoconfigure")

dependencies {
  implementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:library"))

  library("software.amazon.awssdk:aws-core:2.2.0")
  library("software.amazon.awssdk:aws-json-protocol:2.2.0")

  testImplementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:testing"))

  testLibrary("software.amazon.awssdk:dynamodb:2.2.0")
  testLibrary("software.amazon.awssdk:ec2:2.2.0")
  testLibrary("software.amazon.awssdk:kinesis:2.2.0")
  testLibrary("software.amazon.awssdk:lambda:2.2.0")
  testLibrary("software.amazon.awssdk:rds:2.2.0")
  testLibrary("software.amazon.awssdk:s3:2.2.0")
  testLibrary("software.amazon.awssdk:secretsmanager:2.2.0")
  testLibrary("software.amazon.awssdk:sfn:2.2.0")
  testLibrary("software.amazon.awssdk:sns:2.2.0")
  testLibrary("software.amazon.awssdk:sqs:2.2.0")
}

testing {
  suites {
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
    systemProperty("otel.instrumentation.aws-sdk.experimental-span-attributes", true)
    systemProperty("otel.instrumentation.aws-sdk.experimental-record-individual-http-error", true)
    systemProperty("otel.instrumentation.messaging.experimental.capture-headers", "Test-Message-Header")
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  val testRdsDataStableSemconv = register<Test>("testRdsDataStableSemconv") {
    val testRdsDataSourceSet = sourceSets["testRdsData"]
    testClassesDirs = testRdsDataSourceSet.output.classesDirs
    classpath = testRdsDataSourceSet.runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testing.suites, testStableSemconv, testRdsDataStableSemconv)
  }
}

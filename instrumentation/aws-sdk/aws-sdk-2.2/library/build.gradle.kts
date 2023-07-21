plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")

  implementation(project(":instrumentation:message-handler:message-handler-1.0:library"))
  implementation(project(":instrumentation:aws-lambda:aws-lambda-events-2.2:library"))

  library("software.amazon.awssdk:aws-core:2.20.0")
  library("software.amazon.awssdk:aws-json-protocol:2.2.0")
  library("software.amazon.awssdk:sqs:2.20.0")

  testImplementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:testing"))

  latestDepTestLibrary("software.amazon.awssdk:aws-core:+")
  latestDepTestLibrary("software.amazon.awssdk:aws-json-protocol:+")
  latestDepTestLibrary("software.amazon.awssdk:dynamodb:+")
  latestDepTestLibrary("software.amazon.awssdk:ec2:+")
  latestDepTestLibrary("software.amazon.awssdk:kinesis:+")
  latestDepTestLibrary("software.amazon.awssdk:rds:+")
  latestDepTestLibrary("software.amazon.awssdk:s3:+")
  latestDepTestLibrary("software.amazon.awssdk:sqs:+")
}

tasks {
  test {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    systemProperty("otel.instrumentation.aws-sdk.experimental-span-attributes", true)
  }
}
